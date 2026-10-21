package com.explosionwand;

/**
 * Чистая логика выбора места подземного данжа (без классов Minecraft, проверяет DungeonTest) и политики урона по мобам данжей.
 *
 * Разлом привязан к одной точке (вход, y = земля под ней), а тянется на ~170x200 блоков. Если рядом с входом холм, а вокруг ниже,
 * крыша первого уровня торчит над землёй. Поэтому по всей рамке берутся высоты земли (по колонне 16x16 маски, где есть блоки ниже
 * кровли), и место годится, когда нигде земля не ниже кровли больше чем на COVER_OK; хуже COVER_MAX - точка отвергается.
 */
final class DungeonSite {
    private DungeonSite() {}

    /** Допустимая «выставленность» крыши (блоков) для досрочного выбора точки и жёсткий предел для лучшей из точек. */
    static final int COVER_OK = 2, COVER_MAX = 5;
    /** Высота земли неизвестна (чанк не загружен и оценка недоступна): колонна пропускается. */
    static final int UNKNOWN = Integer.MIN_VALUE;
    /** Колонна под водой (озеро, река): кровля упёрлась бы в воду, колонна считается выставленной. */
    static final int WET = Integer.MIN_VALUE + 1;
    /** Выставленность мокрой колонны. */
    static final int WET_EXPOSURE = 99;

    /**
     * Насколько кровля данжа торчит над землёй: max по колоннам (якорь + roof - земля), не меньше 0.
     * anchorY - y входа (y=0 данжа), roof - кровля подземной части относительно входа (Разлом: -4), ground - высота земли по колоннам
     * (первый свободный y над землёй; UNKNOWN пропускается).
     */
    static int exposure(int anchorY, int roof, int[] ground) {
        int worst = 0;
        for (int g : ground) {
            if (g == UNKNOWN) continue;
            if (g == WET) { worst = Math.max(worst, WET_EXPOSURE); continue; }
            worst = Math.max(worst, anchorY + roof - g);
        }
        return worst;
    }

    /** Лучшая точка из списка оценок «выставленности»: первая не хуже COVER_OK, иначе наименьшая не хуже COVER_MAX, иначе -1 (искать другое). */
    static int choose(int[] exposures) {
        int best = -1;
        for (int i = 0; i < exposures.length; i++) {
            if (exposures[i] <= COVER_OK) return i;
            if (exposures[i] <= COVER_MAX && (best < 0 || exposures[i] < exposures[best])) best = i;
        }
        return best;
    }

    /**
     * Урон среды, который гасится у мобов данжа: стены, падения, утопление, сдавливание; огонь (inFire, onFire) - если моб не горит от палочки
     * (жаровня не фарм, солнце не жжёт хосты; поджог палочкой - RiftMobs.ignite). Молнии палочек проходят.
     */
    static boolean envBlockedForMobs(String sourceId, boolean wandFire) {
        if (sourceId == null) return false;
        if (sourceId.equals("inFire") || sourceId.equals("onFire")) return !wandFire;
        return sourceId.equals("inWall") || sourceId.equals("fall") || sourceId.equals("cramming")
                || sourceId.equals("drown") || sourceId.equals("flyIntoWall");
    }
}
