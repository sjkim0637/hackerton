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
    val asset: String,
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
 * 공급받은 화보 atlas 두 장에서 한 페이지씩 잘라 쓴다.
 * `interior_magazine1.png`, `interior_magazine2.png`는 3열 격자에 완성된 잡지 지면이 들어 있다.
 * 좌표는 원본 이미지 기준 0..1 비율이라 화면 크기가 달라져도 같은 자리를 가리킨다.
 */
class MockMagazineFeedProvider : MagazineFeedProvider {
    override suspend fun pages(): List<MagazinePage> = listOf(
        MagazinePage(
            id = "my-home-my-style",
            asset = M1,
            issue = "SEPTEMBER · LIVING",
            title = "공간이 달라지면 일상이 특별해집니다",
            description = "",
            crop = AtlasCrop(0.0024f, 0.0011f, 0.3314f, 0.2868f),
            objects = listOf(
                MagazineObject("sofa-stone-01", "3인 패브릭 소파", "sofa", 2.20f, 0.78f, 0.95f, "floor", 0.22f, 0.68f),
                MagazineObject("table-stone-01", "라운드 스톤 테이블", "table", 0.95f, 0.35f, 0.95f, "floor", 0.55f, 0.78f),
                MagazineObject("lamp-mood-01", "무드 테이블 조명", "lamp", 0.30f, 0.45f, 0.30f, "floor", 0.60f, 0.53f),
            ),
        ),
        MagazinePage(
            id = "modern-style",
            asset = M1,
            issue = "MODERN STYLE",
            title = "모던한 감각, 세련된 일상",
            description = "",
            crop = AtlasCrop(0.6698f, 0.0011f, 0.9976f, 0.2868f),
            objects = listOf(
                MagazineObject("sofa-modern-01", "모던 3인 소파", "sofa", 2.30f, 0.75f, 0.92f, "floor", 0.35f, 0.70f),
                MagazineObject("table-round-01", "라운드 커피 테이블", "table", 0.90f, 0.36f, 0.90f, "floor", 0.58f, 0.80f),
                MagazineObject("lamp-arch-01", "아치 플로어 램프", "lamp", 0.55f, 1.95f, 0.42f, "floor", 0.72f, 0.45f),
                MagazineObject("art-abstract-01", "추상 월 아트", "decor", 0.80f, 1.10f, 0.05f, "wall", 0.27f, 0.42f),
            ),
        ),
        MagazinePage(
            id = "natural-dining",
            asset = M1,
            issue = "NATURAL STYLE",
            title = "자연을 담은 편안한 공간",
            description = "",
            crop = AtlasCrop(0.0024f, 0.2906f, 0.3314f, 0.5601f),
            objects = listOf(
                MagazineObject("table-dining-01", "원목 다이닝 테이블", "table", 1.80f, 0.75f, 0.90f, "floor", 0.55f, 0.66f),
                MagazineObject("chair-dining-01", "우드 다이닝 체어", "chair", 0.48f, 0.82f, 0.52f, "floor", 0.25f, 0.78f),
                MagazineObject("lamp-dome-01", "돔 펜던트 조명", "lamp", 0.42f, 0.32f, 0.42f, "wall", 0.62f, 0.42f),
            ),
        ),
        MagazinePage(
            id = "quiet-bedroom",
            asset = M1,
            issue = "BEDROOM",
            title = "온전한 휴식을 위한 나만의 침실",
            description = "",
            crop = AtlasCrop(0.3396f, 0.2906f, 0.6604f, 0.5601f),
            objects = listOf(
                MagazineObject("bed-queen-01", "퀸 패브릭 베드", "bed", 1.65f, 0.95f, 2.10f, "floor", 0.45f, 0.75f),
                MagazineObject("lamp-linen-01", "리넨 테이블 램프", "lamp", 0.28f, 0.48f, 0.28f, "floor", 0.79f, 0.585f),
                MagazineObject("plant-indoor-01", "실내 화분", "decor", 0.45f, 1.30f, 0.45f, "floor", 0.93f, 0.45f),
            ),
        ),
        MagazinePage(
            id = "warm-kitchen",
            asset = M1,
            issue = "KITCHEN & DINING",
            title = "맛있는 일상이 머무는 곳",
            description = "",
            crop = AtlasCrop(0.6698f, 0.2906f, 0.9976f, 0.5601f),
            objects = listOf(
                MagazineObject("island-wood-01", "우드 아일랜드", "table", 1.90f, 0.92f, 0.85f, "floor", 0.62f, 0.68f),
                MagazineObject("stool-bar-01", "바 스툴", "chair", 0.42f, 0.75f, 0.42f, "floor", 0.53f, 0.78f),
                MagazineObject("lamp-cone-01", "코니컬 펜던트 조명", "lamp", 0.30f, 0.35f, 0.30f, "wall", 0.73f, 0.33f),
            ),
        ),
        MagazinePage(
            id = "balcony-outdoor",
            asset = M1,
            issue = "BALCONY & OUTDOOR",
            title = "바깥의 풍경이 가까워지는 공간",
            description = "",
            crop = AtlasCrop(0.6698f, 0.5644f, 0.9976f, 0.8193f),
            objects = listOf(
                MagazineObject("chair-rattan-01", "라탄 라운지 체어", "chair", 0.78f, 0.85f, 0.80f, "floor", 0.78f, 0.62f),
                MagazineObject("table-side-01", "원형 사이드 테이블", "table", 0.45f, 0.50f, 0.45f, "floor", 0.61f, 0.64f),
                MagazineObject("planter-outdoor-01", "야외 화분", "decor", 0.50f, 0.95f, 0.50f, "floor", 0.27f, 0.78f),
            ),
        ),
        MagazinePage(
            id = "living-together",
            asset = M2,
            issue = "LIVING ROOM",
            title = "머무는 순간이 더 편안한 거실",
            description = "",
            crop = AtlasCrop(0.6682f, 0.0011f, 0.9977f, 0.2939f),
            objects = listOf(
                MagazineObject("sofa-couch-01", "모듈 카우치 소파", "sofa", 2.80f, 0.72f, 1.60f, "floor", 0.42f, 0.68f),
                MagazineObject("table-oval-01", "오벌 우드 테이블", "table", 1.10f, 0.32f, 0.70f, "floor", 0.47f, 0.79f),
                MagazineObject("plant-large-01", "대형 실내 화분", "decor", 0.55f, 1.50f, 0.55f, "floor", 0.53f, 0.48f),
            ),
        ),
        MagazinePage(
            id = "kids-room",
            asset = M2,
            issue = "KIDS ROOM",
            title = "아이의 상상이 자라는 공간",
            description = "",
            crop = AtlasCrop(0.6682f, 0.2961f, 0.9977f, 0.5743f),
            objects = listOf(
                MagazineObject("bed-house-01", "하우스 프레임 침대", "bed", 1.00f, 1.45f, 1.95f, "floor", 0.58f, 0.60f),
                MagazineObject("rug-round-01", "라운드 러그", "decor", 1.60f, 0.02f, 1.60f, "floor", 0.55f, 0.82f),
                MagazineObject("lamp-globe-01", "글로브 펜던트 조명", "lamp", 0.35f, 0.35f, 0.35f, "wall", 0.87f, 0.13f),
            ),
        ),
    )

    private companion object {
        const val M1 = "magazine/interior_magazine1.png"
        const val M2 = "magazine/interior_magazine2.png"
    }
}
