package coverletter.output;

import java.time.LocalDate;

// 저장 파일명 / 헤더용 회사 정보
public record CompanyInfo(String companyName, LocalDate appliedDate) {}
