package coverletter;

// 임시 확인용 - 실행 결과 확인 후 이 파일은 삭제 (커밋 대상 아님)
public class GuideCheck {

	public static void main(String[] args) throws Exception {
		SectionGuideStore store = new SectionGuideStore();
		for(String title : new String[] {"성장과정", "지원동기", "성격 장단점", "입사 후 포부", "모르는 항목"}) {
			SectionGuideStore.SectionGuide g = store.get(title);
			System.out.println(title + "| fallback=" + g.fallback() + "| " + g.writingGuide().substring(0, Math.min(30, g.writingGuide().length())) + "...");
		}
	}
}
