# Preliminary Patent/FTO Notes

이 문서는 법률 의견이나 Freedom-to-Operate 결론이 아니다. 구현 범위와 검토가 필요한 기능을 기록하기 위한 기술 메모다.

초기 지시문은 Orientation/Pose를 Retrieval 또는 Unlock 조건으로 사용하지 않도록 제한했다. 이후 제품 요구사항은 6DoF에 따라 실제 노출 콘텐츠가 달라져야 한다고 정정됐다.

현재 구현은 다음과 같이 경계를 둔다.

- 서버의 상업 Inventory는 Geo × Time Window다.
- 원시 Pose는 서버 Inventory 구매 단위나 영구 검색 키가 아니다.
- 기기에서 이미 조회한 후보의 공간 가시성을 거리와 Camera View Cone으로 판정한다.
- 접근 거리나 방향을 별도 Unlock/결제 조건으로 판매하는 기능은 구현하지 않았다.

향후 아래 기능을 추가하기 전에는 관련 청구항과 관할권을 전문 변리사와 다시 검토해야 한다.

- 특정 방향 일치를 콘텐츠 Unlock 조건으로 사용
- AR 아이콘 접근을 결제 또는 권리 획득 조건으로 사용
- 방향·Pose 구간 자체를 Commercial Inventory로 판매
- 제3자 특허 문헌과 유사한 사용자 흐름을 그대로 구현
