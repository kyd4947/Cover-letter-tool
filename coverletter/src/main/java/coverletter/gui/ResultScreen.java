package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import coverletter.Main;
import coverletter.output.OutputSaver.SectionDraft;

// 결과 화면 - 항목별 탭 + 글자수 + 항목별 다듬기 + 저장 파일 열기
public class ResultScreen extends JPanel {

	private final JTabbedPane tabs = new JTabbedPane();
	private final JLabel hint = new JLabel("수정 지시를 반복하면 저장 파일도 함께 갱신됩니다.");
	private final List<JButton> refineButtons = new ArrayList<>();
	private final Consumer<String> status;
	private Main.GenerationResult result;
	
	public ResultScreen(Consumer<String> status) {
		this.status = status;
		setLayout(new BorderLayout(8, 8));
		add(tabs, BorderLayout.CENTER);
		
		JButton open = new JButton("저장 파일 열기");
		open.addActionListener(e -> openSaved());
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bottom.add(open);
		bottom.add(hint);
		add(bottom, BorderLayout.SOUTH);
	}
	
	// 생성/다듬기 완료 시 GuiMain이 부름 - 탭 재구성
	void showResult(Main.GenerationResult r) {
		result = r;
		tabs.removeAll();
		refineButtons.clear();
		
		for(int i = 0; i < r.drafts().size(); i++) {
			SectionDraft draft = r.drafts().get(i);
			int index = i;
			
			JTextArea view = new JTextArea(draft.text());
			view.setEditable(false);
			view.setLineWrap(true);
			view.setWrapStyleWord(true);
			view.setCaretPosition(0);
			
			JLabel count = new JLabel("제한 " + draft.section().charLimit() + "자 / 현재 " + draft.charCount() + "자");
			JButton refine = new JButton("이 항목 다듬기");
			refine.addActionListener(e -> refine(index));
			refineButtons.add(refine);
			
			JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
			south.add(count);
			south.add(refine);
			
			JPanel panel = new JPanel(new BorderLayout(8, 8));
			panel.add(new JScrollPane(view), BorderLayout.CENTER);
			panel.add(south, BorderLayout.SOUTH);
			tabs.addTab(draft.section().title(), panel);
		}
	}
	
	// 수정 지시 입력 -> 백그라운드 재호출 -> 저장 파일 갱신 + 탭 재구성
	private void refine(int index) {
		if(result == null) {
			return;
		}
		String instruction = JOptionPane.showInputDialog(this,
				"수정 지시 (예: 더 구체적으로, 수치를 강조, 문장을 짧게)",
				"이 항목 다듬기", JOptionPane.PLAIN_MESSAGE);
		if(instruction == null || instruction.isBlank()) {
			return;
		}
		setBusy(true);
		status.accept("다듬기 중 ...");
		
		new SwingWorker<Main.GenerationResult, Void>() {
			@Override
			protected Main.GenerationResult doInBackground() throws Exception {
				return Main.refine(result, index, instruction,
						msg -> SwingUtilities.invokeLater(() -> status.accept(msg)));
			}
			@Override
			protected void done() {
				setBusy(false);
				try {
					Main.GenerationResult updated = get();
					showResult(updated);
					tabs.setSelectedIndex(index);
					status.accept("다듬기 완료 - " + updated.saved().getFileName());
				} catch(Exception e) {
					Throwable c = e.getCause() != null ? e.getCause() : e;
					status.accept("다듬기 실패 - " + c.getMessage());
					JOptionPane.showMessageDialog(ResultScreen.this, c.getMessage(), "다듬기 실패", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}
	
	private void setBusy(boolean busy) {
		for(JButton b : refineButtons) {
			b.setEnabled(!busy);
		}
	}
	
	private void openSaved() {
		if (result == null) {
			return;
		}
		try {
			Desktop.getDesktop().open(result.saved().toFile());
		} catch (Exception e) {
			status.accept("파일 열기 실패 - " + e.getMessage());
		}
	}
}
