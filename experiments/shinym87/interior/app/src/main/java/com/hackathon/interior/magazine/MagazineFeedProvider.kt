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

/**
 * 실제 화보 사진이 준비되기 전까지 쓰는 임시 데이터.
 * crop 영역은 `interior_asset_BG.png`에서 로고, 홍보 문구, 화살표 버튼이 들어가지 않는
 * 순수한 사진 부분만 고른 값이다. `assets/magazine/<id>.jpg`가 생기면 그 사진이 우선한다.
 */
class MockMagazineFeedProvider : MagazineFeedProvider {
    override suspend fun pages(): List<MagazinePage> = listOf(
        MagazinePage(
            id = "warm-reading-room",
            issue = "SEPTEMBER · LIVING",
            title = "빛이 머무는 독서 공간",
            description = "오후 볕이 벽을 타고 내려오는 자리에 라운지 체어 한 점을 두었다. 사진 속 점을 눌러 내 방에서 크기를 확인해 보세요.",
            crop = AtlasCrop(0.0195f, 0.1641f, 0.2799f, 0.4102f),
            objects = listOf(
                MagazineObject("lounge-chair-01", "라운지 체어", "chair", 0.82f, 0.88f, 0.78f, "floor", 0.363f, 0.640f),
                MagazineObject("side-table-01", "마블 사이드 테이블", "table", 0.44f, 0.52f, 0.44f, "floor", 0.845f, 0.660f),
                MagazineObject("floor-lamp-01", "아치 플로어 램프", "lamp", 0.42f, 1.65f, 0.42f, "floor", 0.845f, 0.100f),
            ),
        ),
        MagazinePage(
            id = "soft-neutral-living",
            issue = "NEW COLLECTION · SOFT NEUTRAL",
            title = "낮은 채도의 편안한 거실",
            description = "채도를 낮춘 패브릭과 짙은 원목이 만나는 거실. 소파와 테이블, 화분을 하나씩 눌러 배치를 가늠해 보세요.",
            crop = AtlasCrop(0.7474f, 0.0352f, 0.8802f, 0.2021f),
            objects = listOf(
                MagazineObject("sectional-sofa-01", "모듈 소파", "sofa", 2.45f, 0.82f, 0.95f, "floor", 0.598f, 0.637f),
                MagazineObject("coffee-table-01", "라운드 커피 테이블", "table", 0.86f, 0.38f, 0.86f, "floor", 0.147f, 0.772f),
                MagazineObject("palm-planter-01", "야자수 화분", "decor", 0.52f, 1.45f, 0.52f, "floor", 0.868f, 0.257f),
            ),
        ),
        MagazinePage(
            id = "night-lounge",
            issue = "EDITOR'S PICK · NIGHT LOUNGE",
            title = "짙은 톤으로 만든 휴식",
            description = "어두운 벽과 낮게 내린 조명이 만드는 저녁의 방. 점을 누르면 이름이 뜨고, 한 번 더 누르면 AR 작업 화면으로 넘어갑니다.",
            crop = AtlasCrop(0.7721f, 0.2256f, 0.8757f, 0.3867f),
            objects = listOf(
                MagazineObject("accent-chair-01", "월넛 암체어", "chair", 0.76f, 0.86f, 0.80f, "floor", 0.420f, 0.660f),
                MagazineObject("pendant-light-01", "돔 펜던트 조명", "lamp", 0.48f, 0.34f, 0.48f, "wall", 0.060f, 0.160f),
            ),
        ),
    )
}
