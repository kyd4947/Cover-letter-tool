package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import coverletter.config.AppConfig;

// 온보딩 화면 - 키 없을 때 최초 1회: 안내 -> 키 입력 -> config.json 병합 저장 -> 메인 (G-6)
public class OnboardScreen extends JPanel {

	private final JPasswordField keyField = new JPasswordField();
	private final Consumer<String> status;
	private final Runnable onDone;

	public OnboardScreen(Consumer<String> status, Runnable onDone) {
		this.status = status;
		this.onDone = onDone;
		setLayout(new BorderLayout(8, 8));

		JPanel intro = new JPanel(new GridLayout(0, 1, 4, 4));
		intro.add(new JLabel("환영합니다 - 자기소개서 생성기"));
		intro.add(new JLabel("시작하려면 Gemini API 키가 필요합니다."));
		intro.add(new JLabel("발급 : https://aistudio.google.com/apikey (Google AI Studio)"));
		intro.add(new JLabel("환경변수 GEMINI_API_KEY가 있으면 그 값이 우선이라 키 입력은 생략 가능합니다."));

		JPanel form = new JPanel(new BorderLayout(8, 8));
		form.add(new JLabel("Gemini API 키"), BorderLayout.NORTH);
		form.add(keyField, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton save = new JButton("저장하고 시작");
		save.addActionListener(e -> save());
		south.add(save);

		JPanel top = new JPanel(new BorderLayout(8, 8));
		top.add(intro, BorderLayout.NORTH);
		top.add(form, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(south, BorderLayout.SOUTH);
	}

	// 키를 config.json에 병합 저장 (다른 키 보존) -> 메인 전환
	private void save() {
		String key = new String(keyField.getPassword()).strip();
		if(key.isEmpty()) {
			status.accept("API 키를 입력하세요");
			return;
		}
		try {
			JsonObject json = new JsonObject();
			if(Files.exists(AppConfig.CONFIG_FILE)) {
				try {
					json = JsonParser.parseString(Files.readString(AppConfig.CONFIG_FILE)).getAsJsonObject();
				} catch (Exception e) {
					// 깨진 config 는 빈 객체로 새로 시작
				}
			}
			json.addProperty("geminiApiKey", key);
			Files.writeString(AppConfig.CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json));
			status.accept("키 저장 완료 - 시작합니다");
			onDone.run();
		} catch (Exception e) {
			status.accept("키 저장 실패 - " + e.getMessage());
		}
	}
}
