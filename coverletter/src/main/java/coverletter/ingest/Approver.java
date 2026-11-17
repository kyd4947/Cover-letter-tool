package coverletter.ingest;

import java.util.Scanner;

// 승인 관문 - 질문 -> 사람 판단 (CLI : Scanner, GUI : 다이얼로그) ask는 블로킹 제약
public interface Approver {

	boolean ask(String question);
	
	// 콘솔용 승인 - 질문 출력 후 한 줄 입력, y만 승인
	static Approver console(Scanner in) {
		return q -> {
			System.out.println(q + " (y/n) : ");
			return in.nextLine().strip().equalsIgnoreCase("y");
		};
	}
}
