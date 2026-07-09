// 진료과 → 진료 유형 매핑 테이블
// PRIVATE(비급여중심형) | INSURANCE(급여중심형) | BALANCED(균형형)
// 이 파일만 수정해서 매핑을 조정할 수 있습니다.

const DEPT_TYPE_MAP = {
  // 비급여중심형
  '피부과':             'PRIVATE',
  '성형외과':           'PRIVATE',
  '안과':               'PRIVATE',
  '미용의학':           'PRIVATE',
  '항노화의학':         'PRIVATE',
  '비만의학':           'PRIVATE',

  // 급여중심형
  '일반의 (GP)':        'INSURANCE',
  '내과':               'INSURANCE',
  '소화기내과':         'INSURANCE',
  '순환기내과':         'INSURANCE',
  '호흡기내과':         'INSURANCE',
  '류마티스내과':       'INSURANCE',
  '종양내과':           'INSURANCE',
  '내분비내과':         'INSURANCE',
  '신장내과':           'INSURANCE',
  '감염내과':           'INSURANCE',
  '혈액내과':           'INSURANCE',
  '소아청소년과':       'INSURANCE',
  '가정의학과':         'INSURANCE',
  '재활의학과':         'INSURANCE',
  '신경과':             'INSURANCE',

  // 균형형
  '이비인후과':         'BALANCED',
  '정형외과':           'BALANCED',
  '산부인과':           'BALANCED',
  '비뇨의학과':         'BALANCED',
  '정신건강의학과':     'BALANCED',
  '외과 (일반외과)':    'BALANCED',
  '흉부외과':           'BALANCED',
  '신경외과':           'BALANCED',
  '마취통증의학과':     'BALANCED',
  '응급의학과':         'BALANCED',
  '스포츠의학':         'BALANCED',
  '통증의학':           'BALANCED',
  '수면의학':           'BALANCED',
};

/**
 * 최종 학과/세부 분과를 기반으로 진료 유형을 반환합니다.
 * @param {string} specialty  - 최종 학과 (예: "피부과", "내과")
 * @param {string} subspecialty - 세부 분과 (선택, 예: "미용의학")
 * @returns {'BALANCED'|'INSURANCE'|'PRIVATE'}
 */
function getDeptClinicType(specialty, subspecialty) {
  if (subspecialty && DEPT_TYPE_MAP[subspecialty]) {
    return DEPT_TYPE_MAP[subspecialty];
  }
  if (specialty && DEPT_TYPE_MAP[specialty]) {
    return DEPT_TYPE_MAP[specialty];
  }
  return 'BALANCED';
}
