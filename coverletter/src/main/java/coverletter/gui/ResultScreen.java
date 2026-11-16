package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.Desktop;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import coverletter.Main;
import coverletter.output.OutputSaver.SectionDraft;

// 결과 화면 - 생성 완료 초안 표시 + 저장 파일 열기
public class ResultScreen extends JPanel {

	private final JTextArea view = new JTextArea();
	private Main.GenerationResult result;
	
	public ResultScreen() {
		setLayout(new BorderLayout(8, 8));
		view.setEditable(false);
		add(new JScrollPane(view), BorderLayout.CENTER);
		
		JButton open = new JButton("저장 파일 열기");
		open.addActionListener(e -> openSaved());
		JPanel bottom = new JPanel();
		bottom.add(open);
		add(bottom, BorderLayout.SOUTH);
	}
	
	// 생성 완료 시 MainScreen이 부름
	void showResult(Main.GenerationResult r) {
		result = r;
		StringBuilder sb = new StringBuilder();
		for(SectionDraft d : r.drafts()) {
			sb.append("## ").append(d.section().title())
			.append(" (제한 ").append(d.section().charLimit())
			.append("자 / 설계 ").append(d.charCount()).append("자)\n\n")
			.append(d.text()).append("\n\n");
		}
		view.setText(sb.toString());
		view.setCaretPosition(0);
	}
	
	private void openSaved() {
		if (result == null) {
			return;
		}
		try {
			Desktop.getDesktop().open(result.saved().toFile());
		} catch (Exception e) {
			view.append("[오류] 파일 열기 실패 - " + e.getMessage() + "\n");
		}
	}
}
