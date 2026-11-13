package coverletter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import coverletter.config.AppConfig;
import coverletter.ingest.Ingestor;

// 레포 적재 1 단계 - 레포 URL -> zip 다운 -> cache / 풀기 -> 텍스트 선별 통계
// LLM 미사용 - 요약 카드 / 승인 반영은 2 단계 곡에서 연결
public class RepoIngestMain {

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	
	// 단일 파일 한계
	private static final long MAX_FILE_BYTES = 1024 * 1024;
	private static final Path CACHE = Path.of("cache");
	
	// 텍스트 선별 확장자
	private static final List<String> TEXT_EXTS = List.of(
			"md", "txt", "java", "kt", "kts", "gradle", "py", "js", "ts",
			"html", "css", "ipynb", "json", "xml", "yml", "yaml",
			"properties", "csv", "sql", "sh", "bat");
	
	// 이름이 이러면 폴더마다 통째로 제외
	private static final List<String> SKIP_DIRS = List.of(
			".git", ".github", "node_modules", "build", "bin", ".gradle",
			"dist", "__pycache__", "venv", ".idea");
	
	// 레포 스냅샷 전용 - 코드·문서의 실제 흔적만 근거로
		private static final String REPO_SYSTEM = """
				너는 GitHub 레포 스냅샷 분석기다. README·소스·노트북에서 자기소개서 재료로 쓸 사실만 정리한다.
				코드에 실제 흔적이 없는 것을 만들어내지 않는다. 근거 없는 칭찬 표현 금지.
				목적은 README·문서·주석에서, 과제와 결과는 코드에 실제 남은 흔적에서 찾는다.
				프로젝트가 여럿 보이면 각각 카드를 출력하고 카드 사이는 구분줄 === 로 나눈다.
				출력은 아래 양식만, 각 항목은 한 줄:
				제목: (프로젝트명, 20자 이내)
				목적: (만들거나 한 이유)
				기술: (실제 사용한 것)
				과제: (직면한 문제 - 자료에 실제 흔적이 있는 것)
				결과: (자료에 실제 근거가 있는 것만)
				""";
	
	public static void main(String[] args) {
		if(args.length < 1) {
			System.err.println("사용법 : RepoIngestMain <https://github.com/owner/repo> [branch]");
			return;
		}
		try {
			run(args[0], args.length > 1 ? args[1] : null);
		} catch (Exception e) {
			System.out.println("[오류] " + e.getMessage());
		}
	}
	
	private static void run(String url, String branchArg) throws Exception{
		String[] ownerRepo = parseOwnerRepo(url);
		if(ownerRepo == null) {
			throw new IOException("레포 URL 파싱 실패 : " + url);
		}
		String owner = ownerRepo[0], repo = ownerRepo[1];
		// URL에 /tree/브랜치 가 있으면 그걸 쓰고, 없으면 인자, 그것도 없으면 기본 브랜치 조회
		String target = ownerRepo[2] != null ? ownerRepo[2] : branchArg;
		if(target == null) {
			target = defaultBranch(owner, repo);
		}
		System.out.println("레포 : " + owner + "/" + repo + " (branch " + target + ")");
		
		Path dir = CACHE.resolve(owner + "-" + repo);
		if(Files.exists(dir)) {
			System.out.println("cache/ 스냅샷 이미 존재 - 재사용 (새로 받으려면 cache 폴더 삭제 후 재Run)");
		} else {
			// codeload 직결 - api.github.com rate (비로그인 60/h)를 소모하지 않음
			URI zipUri = URI.create("https://codeload.github.com/" + owner + "/" + repo + "/zip/refs/heads/" + target);
			HttpResponse<InputStream> res = HTTP.send(HttpRequest.newBuilder().uri(zipUri).header("User-Agent", "coverletter-tool")
					.timeout(Duration.ofSeconds(120)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			if(res.statusCode() != 200) {
				throw new IOException("스냅샷 다운로드 실패 : HTTP " + res.statusCode() + " - URL, 브랜치, 공개 여부 확인");
			}
			System.out.println("zip 내려 받는 중 ....");
			try (InputStream in = res.body()){
				unzip(in, dir);
			}
		}
		finish(dir);
		
		// 2 단계 - 선별 문서를 LLM 요약 카드로 (Ingestor 공유 승인 관문)
		String doc = assembleDoc(dir);
		if(doc.isBlank()) {
			System.out.println("[스킵] " + repo + " - 텍스트 재료 없음");
			return;
		}
		AppConfig config = AppConfig.load();
		System.out.println("키 로딩 OK - 모델 : " + config.models().get(0));
		new Ingestor(config.profilePath()).ingestText(config, owner + "/" + repo + " (레포 스냅샷)", repo, doc);
	}
	
	// 기본 브랜치 조회 - GitHub API를 딱 1회 (비로그인 rate 60/h, 무해)
	private static String defaultBranch(String owner, String repo) throws IOException, InterruptedException{
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder().uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo))
				.header("User-Agent", "coverletter-tool").timeout(Duration.ofSeconds(30)).GET().build(), 
				HttpResponse.BodyHandlers.ofString());
		if(res.statusCode() != 200) {
			throw new IOException("레포 조회 실패 : HTTP " + res.statusCode() + (res.statusCode() == 404 ? " - URL 또는 private 레포 여부 확인" : ""));
		}
		JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
		return root.has("default_branch") && !root.get("default_branch").isJsonNull()
				? root.get("default_branch").getAsString() : "main";
	}
	
	// 허용 : github.com/owner/repo[/tree/브랜치] | owner/repo[/tree/브랜치]
	// 반환 {owner, repo, branch(명시 표기 시에만, 그 외 null)}
	private static String[] parseOwnerRepo(String input) {
		String s = input.strip();
		int treeAt = s.indexOf("/tree/");
		String branch = null;
		if(treeAt >= 0) {
			branch = s.substring(treeAt + "/tree/".length()).strip();
			s = s.substring(0, treeAt);
		}
		int at = s.indexOf("github.com/");
		if(at >= 0) {
			s = s.substring(at + "github.com/".length());
		}
		String[] parts = s.split("/");
		if(parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
			return null;
		}
		String repo = parts[1].strip();
		if(repo.endsWith(".git")) {
			repo = repo.substring(0, repo.length() - 4);
		}
		return new String[] { parts[0].strip(), repo, branch };
	}
	
	// zip -> cache/owner-repo/ (최상위 1단 제거 + zip-slip 방어)
	private static void unzip(InputStream in, Path dest) throws IOException{
		Files.createDirectories(dest);
		try(ZipInputStream zip = new ZipInputStream(in)){
			for(ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
				String[] parts = entry.getName().split("/");
				Path rel = parts.length > 1 ? Path.of(String.join("/", Arrays.copyOfRange(parts, 1, parts.length))).normalize()
						: Path.of(".");
				if(rel.toString().equals(".")) {
					continue;			// 최상위 폴더 엔트리자체
				}
				Path target = dest.resolve(rel).normalize();
				if(!target.startsWith(dest)) {
					throw new IOException("zip 경로 이상 (slip 의심) : " + entry.getName());
				}
				if(entry.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}
	
	// 선별 파일 경로순 정렬 -> "===== 경로 =====" 헤더 + 내용 연결 (4MB 조립 한도)
	private static String assembleDoc(Path dir) throws IOException{
		List<Path> files = new ArrayList<>();
		for(Path file : Files.walk(dir).toList()) {
			if(Files.isRegularFile(file) && !isSkipped(file) && TEXT_EXTS.contains(extOf(file))
					&& Files.size(file) <= MAX_FILE_BYTES) {
				files.add(file);
			}
		}
		files.sort(Path::compareTo);
		StringBuilder doc = new StringBuilder();
		for(Path file : files) {
			String content = Files.readString(file);
			if(doc.length() + content.length() > 4L * 1024 * 1024) {
				doc.append("\n\n[참고] 문서 조립 한도 도달 - 나머지 파일 생략\n");
				break;
			}
			doc.append("\n\n===== ").append(dir.relativize(file)).append(" =====\n").append(content);
		}
		return doc.toString().strip();
	}
	
	// 선별 통계 스탬프 - 파일 수, 총량, 확장자 분포 상위
	private static void finish(Path dir) throws IOException{
		int kept = 0, skipped = 0;
		long total = 0;
		Map<String, Integer> dist = new HashMap<>();
		for(Path file : Files.walk(dir).toList()) {
			if(!Files.isRegularFile(file)) {
				continue;
			}
			if(isSkipped(file) || !TEXT_EXTS.contains(extOf(file)) || Files.size(file) > MAX_FILE_BYTES) {
				skipped++;
				continue;
			}
			kept++;
			total += Files.size(file);
			dist.merge(extOf(file), 1, Integer::sum);
		}
		System.out.println("텍스트 대상 " + kept + "개 · " + (total / 1024) + " KB · 제외 " + skipped + "개 (무시 폴더 · 1MB 초과)");
		List<Map.Entry<String, Integer>> top = new ArrayList<>(dist.entrySet());
		top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		for(Map.Entry<String, Integer> e : top.subList(0, Math.min(6, top.size()))) {
			System.out.println(" 확장자 " + e.getKey() + " : " + e.getValue() + "개");
		}
		System.out.println("스냅샷 위치 : " + dir.toAbsolutePath());
	}
	
	// 경로 중간 어디라도 SKIP_DIRS 면 제외
	private static boolean isSkipped(Path file) {
		for(Path part : file) {
			if(SKIP_DIRS.contains(part.toString().toLowerCase())) {
				return true;
			}
		}
		return false;
	}
	
	private static String extOf(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
	}
}