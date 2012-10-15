package de.bangl.lm;

public class LotGroup {
    private String groupId;
    private Integer limit;
    private Double lotPrice;

    /**
     *
     * @param id
     * @param limit
     * @param lotPrice
     */
    public LotGroup(String groupId, Integer limit, Double lotPrice) {
        this.groupId = groupId;
        this.limit = limit;
        this.lotPrice = lotPrice;
    }

    public String getId() {
        return this.groupId;
    }

    public Integer getLimit() {
        return this.limit;
    }

    public Double getLotPrice() {
        return this.lotPrice;
    }
}
