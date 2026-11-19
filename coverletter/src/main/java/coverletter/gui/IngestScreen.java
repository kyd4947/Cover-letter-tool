package coverletter.gui;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import coverletter.IngestMain;
import coverletter.RepoIngestMain;
import coverletter.config.AppConfig;
import coverletter.ingest.Ingestor;

// 적재 화면 - 자료 선택 -> 카드 관문 -> 다이얼로그 승인 (G-4b)
public class IngestScreen extends JPanel {

	private final JTextField pathField = new JTextField();
	private final JTextArea log = new JTextArea();
	private final JButton runButton = new JButton("적재 시작");
	private final Consumer<String> status;		// 하단 상태바 콜백
	
	public IngestScreen(Consumer<String> status) {
		this.status = status;
		setLayout(new BorderLayout(8, 8));
		
		JPanel input = new JPanel(new BorderLayout(8, 8));
		JPanel row = new JPanel(new BorderLayout(8, 8));
		row.add(new JLabel("자료 (파일/폴더 경로 또는 GitHub 레포 URL)"), BorderLayout.NORTH);
		row.add(pathField, BorderLayout.CENTER);
		JButton browse = new JButton("찾아보기...");
		browse.addActionListener(e -> browse());
		row.add(browse, BorderLayout.EAST);
		input.add(row, BorderLayout.CENTER);
		input.add(runButton, BorderLayout.EAST);
		
		log.setEditable(false);
		
		add(input, BorderLayout.NORTH);
		add(new JScrollPane(log), BorderLayout.CENTER);
		
		runButton.addActionListener(e -> run());
	}
	
	// 파일/폴더 선택기 - 선택 결과를 입력칸에 채움
	private void browse() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			pathField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}
	
	// 적재 흐름 - 백그라운드 스레드, 승인은 다이얼로그 (블로킹)
	private void run() {
		String arg = pathField.getText().strip();
		if(arg.isEmpty()) {
			status.accept("자료 경로를 입력하세요");
			return;
		}
		runButton.setEnabled(false);
		log.setText("");
		status.accept("적재 중 ...");
		Consumer<String> logOut = msg -> SwingUtilities.invokeLater(() -> log.append(msg + "\n"));
		
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				if(arg.startsWith("http")) {
					RepoIngestMain.run(arg, null, new DialogApprover(IngestScreen.this), logOut);
					return null;
				}
				AppConfig config = AppConfig.load();
				List<Path> files = new ArrayList<>();
				IngestMain.collectFiles(arg, files, logOut);
				if(files.isEmpty()) {
					logOut.accept("적재할 파일이 없습니다.");
					return null;
				}
				new Ingestor(config.profilePath()).ingest(config, files, new DialogApprover(IngestScreen.this), logOut);
				return null;
			}
			@Override
			protected void done() {
				runButton.setEnabled(true);
				try {
					get();
					status.accept("적재 완료");
				} catch (Exception e) {
					Throwable c = e.getCause() != null ? e.getCause() : e;
					log.append("[오류] " + c.getMessage() + "\n");
					status.accept("오류 - 로그 확인");
				}
			}
		}.execute();
	}
}
