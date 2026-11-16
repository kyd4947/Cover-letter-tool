package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import coverletter.Main;

// 메인 화면 - 공고 입력 -> 생성 흐름 백그라운드 실행 -> 로그 패널 출력
public class MainScreen extends JPanel{

	private final JTextField postingField = new JTextField();
	private final JTextField companyField = new JTextField();
	private final JTextArea log = new JTextArea();
	private final JButton runButton = new JButton("생성 시작");
	private final Consumer<String> status;				// 하단 상태바 콜백
	
	public MainScreen(Consumer<String> status) {
		this.status = status;
		setLayout(new BorderLayout(8, 8));
		
		JPanel input = new JPanel(new GridLayout(2, 2, 8, 8));
		input.add(new JLabel("공공 (URL 또는 텍스트 파일 경로)"));
		input.add(postingField);
		input.add(new JLabel("회사명"));
		input.add(companyField);
		
		log.setEditable(false);
		
		JPanel top = new JPanel(new BorderLayout(8, 8));
		top.add(input, BorderLayout.CENTER);
		top.add(runButton, BorderLayout.EAST);
		
		add(top, BorderLayout.NORTH);
		add(new JScrollPane(log), BorderLayout.CENTER);
		
		runButton.addActionListener(e -> run());
	}
	
	// 생성 흐름 - 백그라운드 스레드
	private void run() {
		String arg0 = postingField.getText().strip();
		String company = companyField.getText().strip();
		if(arg0.isEmpty() || company.isEmpty()) {
			status.accept("공고와 회사명을 입력하세요.");
			return;
		}
		runButton.setEnabled(false);
		log.setText("");
		status.accept("생성 중 ...");
		
		new SwingWorker<Main.GenerationResult, Void>(){
			@Override
			protected Main.GenerationResult doInBackground() throws Exception {
				return Main.generate(arg0, company, msg ->
						SwingUtilities.invokeLater(() -> log.append(msg + "\n")));
			}
			@Override
			protected void done() {
				runButton.setEnabled(true);
				try {
					Main.GenerationResult r = get();
					if(r == null) {
						status.accept("생성 중단 - 로그 확인");
					} else {
						status.accept("완료 - " + r.saved().toAbsolutePath());
					}
				} catch(Exception e) {
					Throwable c = e.getCause() != null ? e.getCause() : e;
					log.append("[오류] " + c.getMessage() + "\n");
					status.accept("오류 - 로그 확인");
				}
			}
		}.execute();
	}
}
