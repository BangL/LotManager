package de.bangl.lm;

public class Lot {

    private String lotId;
    private Integer worldId;
    private LotGroup lotGroup;

    /**
     *
     * @param lotId
     * @param worldId
     * @param lotGroup
     */
    public Lot(final String lotId, final Integer worldId, final LotGroup lotGroup) {
        this.lotId = lotId;
        this.worldId = worldId;
        this.lotGroup = lotGroup;
    }

    public String getId() {
        return this.lotId;
    }

    public Integer getWorldId() {
        return this.worldId;
    }

    public LotGroup getGroup() {
        return this.lotGroup;
    }

}
