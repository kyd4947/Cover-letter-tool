package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import coverletter.config.AppConfig;

// 설정 화면 - config.json 편집 + profile 백업 (G-5)
public class SettingsScreen extends JPanel {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final JPasswordField keyField = new JPasswordField();
	private final JTextField modelsField = new JTextField();
	private final JTextField temperatureField = new JTextField();
	private final JTextField maxTokensField = new JTextField();
	private final JTextField profileField = new JTextField();
	private final JTextField outputField = new JTextField();
	private final Consumer<String> status;

	public SettingsScreen(Consumer<String> status) {
		this.status = status;
		setLayout(new BorderLayout(8, 8));

		JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
		form.add(new JLabel("Gemini API 키 (환경변수 GEMINI_API_KEY 우선)"));
		form.add(keyField);
		form.add(new JLabel("모델 목록 (콤마 구분, 첫 번째가 기본)"));
		form.add(modelsField);
		form.add(new JLabel("temperature (0.0 ~ 2.0)"));
		form.add(temperatureField);
		form.add(new JLabel("maxOutputTokens"));
		form.add(maxTokensField);
		form.add(new JLabel("profile 경로 (STORE)"));
		form.add(profileField);
		form.add(new JLabel("output 경로 (저장 위치)"));
		form.add(outputField);

		JPanel buttons = new JPanel();
		JButton save = new JButton("설정 저장");
		save.addActionListener(e -> save());
		JButton backup = new JButton("profile 백업");
		backup.addActionListener(e -> backupProfile());
		buttons.add(save);
		buttons.add(backup);

		add(form, BorderLayout.NORTH);
		add(buttons, BorderLayout.SOUTH);

		loadCurrent();
	}

	// 현재 config.json 값을 폼에 채움 (없으면 기본값)
	private void loadCurrent() {
		JsonObject json = readJson();
		keyField.setText(str(json, "geminiApiKey", ""));
		modelsField.setText(str(json, "models", "gemini-2.5-flash, gemini-3.8-flash"));
		temperatureField.setText(str(json, "temperature", "0.7"));
		maxTokensField.setText(str(json, "maxOutputTokens", "4096"));
		profileField.setText(str(json, "profilePath", "profile.md"));
		outputField.setText(str(json, "outputDir", "output"));
	}

	// 폼 값 -> config.json 저장 (다음 Run부터 반영)
	private void save() {
		try {
			JsonObject json = new JsonObject();
			String key = new String(keyField.getPassword()).strip();
			if(!key.isEmpty()) {
				json.addProperty("geminiApiKey", key);
			}
			JsonArray models = new JsonArray();
			for(String m : modelsField.getText().split(",")) {
				if(!m.isBlank()) {
					models.add(m.strip());
				}
			}
			if(models.isEmpty()) {
				throw new IllegalArgumentException("모델 목록이 비어 있음");
			}
			json.add("models", models);
			json.addProperty("temperature", Double.parseDouble(temperatureField.getText().strip()));
			json.addProperty("maxOutputTokens", Integer.parseInt(maxTokensField.getText().strip()));
			json.addProperty("profilePath", profileField.getText().strip());
			json.addProperty("outputDir", outputField.getText().strip());
			String text = new GsonBuilder().setPrettyPrinting().create().toJson(json);
			Files.writeString(AppConfig.CONFIG_FILE, text);
			status.accept("설정 저장 완료 - 다음 Run부터 반영");
		} catch (NumberFormatException e) {
			status.accept("숫자 형식 오류 - temperature / maxOutputTokens 확인");
		} catch (Exception e) {
			status.accept("설정 저장 실패 - " + e.getMessage());
		}
	}

	// profile.md -> backup/profile_날짜시간.md 복사
	private void backupProfile() {
		try {
			Path profile = Path.of(profileField.getText().strip());
			if(!Files.exists(profile)) {
				status.accept("백업 실패 - profile 파일 없음 : " + profile);
				return;
			}
			Path dir = profile.getParent() != null ? profile.getParent().resolve("backup") : Path.of("backup");
			Files.createDirectories(dir);
			Path target = dir.resolve("profile_" + LocalDateTime.now().format(STAMP) + ".md");
			Files.copy(profile, target, StandardCopyOption.REPLACE_EXISTING);
			status.accept("백업 완료 : " + target.toAbsolutePath());
		} catch (IOException e) {
			status.accept("백업 실패 - " + e.getMessage());
		}
	}

	// config.json 읽기 - 없거나 깨졌으면 빈 객체 (fail-closed)
	private static JsonObject readJson() {
		try {
			if(Files.exists(AppConfig.CONFIG_FILE)) {
				return JsonParser.parseString(Files.readString(AppConfig.CONFIG_FILE)).getAsJsonObject();
			}
		} catch (Exception e) {
			// 빈 객체 폴백
		}
		return new JsonObject();
	}

	// 폼 표시용 값 - models는 콤마 연결, 그 외는 문자열
	private static String str(JsonObject json, String name, String fallback) {
		if(!json.has(name) || json.get(name).isJsonNull()) {
			return fallback;
		}
		if(json.get(name).isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for(JsonElement e : json.getAsJsonArray(name)) {
				if(!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append(e.getAsString());
			}
			return sb.toString();
		}
		return json.get(name).getAsString();
	}
}
