package com.hackathon.interior.magazine

data class AtlasCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class MagazineObject(
    val id: String,
    val name: String,
    val category: String,
    val widthM: Float,
    val heightM: Float,
    val depthM: Float,
    val anchorHint: String,
    val x: Float,
    val y: Float,
)

data class MagazinePage(
    val id: String,
    val issue: String,
    val title: String,
    val description: String,
    val crop: AtlasCrop,
    val objects: List<MagazineObject>,
)

/** UI는 이 계약만 사용한다. 이후 Web/Miso 구현체로 교체해도 화면 코드는 바뀌지 않는다. */
interface MagazineFeedProvider {
    suspend fun pages(): List<MagazinePage>
}

class MockMagazineFeedProvider : MagazineFeedProvider {
    override suspend fun pages(): List<MagazinePage> = listOf(
        MagazinePage(
            id = "warm-reading-room",
            issue = "SEPTEMBER · LIVING",
            title = "빛이 머무는 독서 공간",
            description = "사진 속 + 표시가 있는 가구를 눌러 내 공간에서 확인해 보세요.",
            crop = AtlasCrop(0.013f, 0.016f, 0.343f, 0.409f),
            objects = listOf(
                MagazineObject("lounge-chair-01", "라운지 체어", "chair", 0.82f, 0.88f, 0.78f, "floor", 0.31f, 0.72f),
                MagazineObject("side-table-01", "마블 사이드 테이블", "table", 0.44f, 0.52f, 0.44f, "floor", 0.65f, 0.74f),
                MagazineObject("floor-lamp-01", "아치 플로어 램프", "lamp", 0.42f, 1.65f, 0.42f, "floor", 0.72f, 0.45f),
            ),
        ),
        MagazinePage(
            id = "soft-neutral-living",
            issue = "NEW COLLECTION · SOFT NEUTRAL",
            title = "낮은 채도의 편안한 거실",
            description = "화보 속 소파와 테이블을 직접 눌러 크기와 배치를 확인하세요.",
            crop = AtlasCrop(0.639f, 0.017f, 0.885f, 0.206f),
            objects = listOf(
                MagazineObject("sectional-sofa-01", "모듈 소파", "sofa", 2.45f, 0.82f, 0.95f, "floor", 0.73f, 0.61f),
                MagazineObject("coffee-table-01", "라운드 커피 테이블", "table", 0.86f, 0.38f, 0.86f, "floor", 0.50f, 0.73f),
                MagazineObject("wall-art-01", "미니멀 월 아트", "decor", 0.72f, 0.92f, 0.05f, "wall", 0.20f, 0.40f),
            ),
        ),
        MagazinePage(
            id = "night-lounge",
            issue = "EDITOR'S PICK · NIGHT LOUNGE",
            title = "짙은 톤으로 만든 휴식",
            description = "가구를 누르면 별도 버튼 없이 바로 AR 작업 화면으로 연결됩니다.",
            crop = AtlasCrop(0.639f, 0.224f, 0.885f, 0.400f),
            objects = listOf(
                MagazineObject("accent-chair-01", "월넛 암체어", "chair", 0.76f, 0.86f, 0.80f, "floor", 0.69f, 0.68f),
                MagazineObject("pendant-light-01", "돔 펜던트 조명", "lamp", 0.48f, 0.34f, 0.48f, "wall", 0.51f, 0.25f),
            ),
        ),
    )
}
