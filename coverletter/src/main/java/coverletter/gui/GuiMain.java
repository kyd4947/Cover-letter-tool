package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

// GUI 골격 - 창 1개 + 카드 전환 + 상태바
public class GuiMain {

	// 화면 이름 상수 - 카드 전환 키 겸용
	private static final String[] SCREENS = {"온보딩", "메인", "적재", "결과", "설정"};
	
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final JLabel status = new JLabel("준비");
	
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
		
		// 화면 뼈대
		for(String name : SCREENS) {
			JPanel p = new JPanel();
			p.add(new JLabel(name + " 화면"));
			deck.add(p, name);
		}
		
		frame.add(nav, BorderLayout.NORTH);
		frame.add(deck, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		
		frame.setSize(900, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	// 화면 전환 + 상태바 갱신
	private void show(String name) {
		cards.show(deck, name);
		status.setText("화면 : " + name);
	}
}
