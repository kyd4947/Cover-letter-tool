package coverletter.gui;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import coverletter.ingest.Approver;

// 다이얼로그 승인 - 카드 통째 표시 후 예/아니요 (백그라운드 스레드에서 블로킹)
public class DialogApprover implements Approver {

	private final Component parent;

	public DialogApprover(Component parent) {
		this.parent = parent;
	}

	@Override
	public boolean ask(String question) {
		boolean[] ok = new boolean[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				JTextArea area = new JTextArea(question, 22, 46);
				area.setEditable(false);
				area.setLineWrap(true);
				area.setWrapStyleWord(true);
				int choice = JOptionPane.showConfirmDialog(parent, new JScrollPane(area),
						"적재 승인", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
				ok[0] = choice == JOptionPane.YES_OPTION;		// 창 닫기(X)도 아니요 취급 - fail-closed
			});
		} catch (Exception e) {
			return false;		// EDT 실패 시 보류 (fail-closed)
		}
		return ok[0];
	}
}
