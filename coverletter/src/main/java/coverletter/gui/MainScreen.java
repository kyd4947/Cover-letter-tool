package coverletter.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import coverletter.Main;
import coverletter.Section;

// 메인 화면 - 공고 입력 -> 생성 흐름 백그라운드 실행 -> 로그 패널 출력
public class MainScreen extends JPanel{

	private final JTextField postingField = new JTextField();
	private final JTextField companyField = new JTextField();
	private final JComboBox<String> formMode = new JComboBox<>(new String[] {"자동 감지", "기본 4항목", "직접 입력"});
	private final JTextField manualFormField = new JTextField();
	private final JTextArea log = new JTextArea();
	private final JButton runButton = new JButton("생성 시작");
	private final Consumer<String> status;				// 하단 상태바 콜백
	private final Consumer<Main.GenerationResult> done;		// 생성 완료 통보 (결과 화면 전환)
	
	public MainScreen(Consumer<String> status, Consumer<Main.GenerationResult> done) {
		this.status = status;
		this.done = done;
		setLayout(new BorderLayout(8, 8));
		
		JPanel input = new JPanel(new GridLayout(3, 2, 8, 8));
		input.add(new JLabel("공고 (URL 또는 텍스트 파일 경로)"));
		input.add(postingField);
		input.add(new JLabel("회사명"));
		input.add(companyField);
		input.add(new JLabel("양식"));
		JPanel formRow = new JPanel(new BorderLayout(8, 8));
		formRow.add(formMode, BorderLayout.WEST);
		formRow.add(manualFormField, BorderLayout.CENTER);
		input.add(formRow);

		manualFormField.setToolTipText("직접 입력 예: 성장과정:500, 지원동기:500, 입사 후 포부:400 (200~2000자, 최대 5개)");
		manualFormField.setEnabled(false);
		formMode.addActionListener(e -> manualFormField.setEnabled(formMode.getSelectedIndex() == 2));
		
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
		List<Section> form;
		try {
			form = switch(formMode.getSelectedIndex()) {
				case 1 -> Main.DEFAULT_FORM;
				case 2 -> parseManualForm(manualFormField.getText());
				default -> null;
			};
		} catch(IllegalArgumentException e) {
			status.accept("양식 오류 - " + e.getMessage());
			return;
		}
		runButton.setEnabled(false);
		log.setText("");
		status.accept("생성 중 ...");
		
		new SwingWorker<Main.GenerationResult, Void>(){
			@Override
			protected Main.GenerationResult doInBackground() throws Exception {
				return Main.generate(arg0, company, form, msg ->
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
						done.accept(r);
					}
				} catch(Exception e) {
					Throwable c = e.getCause() != null ? e.getCause() : e;
					log.append("[오류] " + c.getMessage() + "\n");
					status.accept("오류 - 로그 확인");
				}
			}
		}.execute();
	}

	// 직접 입력 양식 파싱 - "항목명:글자수, ..." (200~2000자, 최대 5개)
	static List<Section> parseManualForm(String text) {
		List<Section> form = new ArrayList<>();
		for(String part : text.split("[,，]")) {
			String s = part.strip();
			if(s.isEmpty()) {
				continue;
			}
			int at = Math.max(s.lastIndexOf(':'), s.lastIndexOf('：'));
			if(at <= 0 || at == s.length() - 1) {
				throw new IllegalArgumentException("'항목명:글자수' 형식 아님 - " + s);
			}
			String title = s.substring(0, at).strip();
			String digits = s.substring(at + 1).replaceAll("[^0-9]", "");
			if(title.isEmpty() || digits.isEmpty()) {
				throw new IllegalArgumentException("'항목명:글자수' 형식 아님 - " + s);
			}
			int limit;
			try {
				limit = Integer.parseInt(digits);
			} catch(NumberFormatException e) {
				throw new IllegalArgumentException("글자수가 너무 큼 - " + s);
			}
			if(limit < 200 || limit > 2000) {
				throw new IllegalArgumentException("글자수는 200~2000 - " + s);
			}
			form.add(new Section(title, limit));
			if(form.size() > 5) {
				throw new IllegalArgumentException("항목은 최대 5개");
			}
		}
		if(form.isEmpty()) {
			throw new IllegalArgumentException("항목을 1개 이상 입력하세요");
		}
		return List.copyOf(form);
	}
}
