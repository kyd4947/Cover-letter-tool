package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import coverletter.Main;
import coverletter.config.AppConfig;

// GUI 골격 - 창 1개 + 카드 전환 + 상태바
public class GuiMain {

	// 화면 이름 상수 - 카드 전환 키 겸용
	private static final String[] SCREENS = {"온보딩", "메인", "적재", "결과", "설정"};
	
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final JLabel status = new JLabel("준비");
	private final ResultScreen resultScreen = new ResultScreen();
	private final IngestScreen ingestScreen = new IngestScreen(status::setText);
	private final SettingsScreen settingsScreen = new SettingsScreen(status::setText);
	private final OnboardScreen onboardScreen = new OnboardScreen(status::setText, () -> show("메인"));
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new GuiMain().start());
	}
	
	private void start() {
		JFrame frame = new JFrame("자기소개서 생성기");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// 상단 내비 - 화면 전환 버튼
		JPanel nav = new JPanel();
		for(String name : SCREENS) {
			JButton b = new JButton(name);
			b.addActionListener(e -> show(name));
			nav.add(b);
		}
		
		// 화면 뼈대 - 전 화면 실물 (G-2~G-6)
		for(String name : SCREENS) {
			JPanel p;
			if(name.equals("온보딩")) {
				p = onboardScreen;
			} else if(name.equals("메인")) {
				p = new MainScreen(status::setText, this::showResult);
			} else if(name.equals("결과")) {
				p = resultScreen;
			} else if(name.equals("적재")) {
				p = ingestScreen;
			} else {
				p = settingsScreen;		// 설정
			}
			deck.add(p, name);
		}
		
		frame.add(nav, BorderLayout.NORTH);
		frame.add(deck, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		
		frame.setSize(900, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
		// 시작 화면 - 키가 있으면 메인, 없으면 온보딩 (G-6)
		try {
			AppConfig.load();
			show("메인");
		} catch (Exception e) {
			show("온보딩");
		}
	}
	
	// 화면 전환 + 상태바 갱신
	private void show(String name) {
		cards.show(deck, name);
		status.setText("화면 : " + name);
	}
	
	// 생성 완료 -> 결과 화면 채움 + 전환
	private void showResult(Main.GenerationResult r) {
		resultScreen.showResult(r);
		show("결과");
	}
}
