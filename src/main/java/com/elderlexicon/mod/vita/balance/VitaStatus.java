package com.elderlexicon.mod.vita.balance;

/**
 * Mutable record of the four Vita pools.
 */
public final class VitaStatus {

    private float aqua;
    private float igni;
    private float aura;
    private float firmo;

    public VitaStatus(float aqua, float igni, float aura, float firmo) {
        this.aqua = aqua;
        this.igni = igni;
        this.aura = aura;
        this.firmo = firmo;
    }

    public float aqua() {
        return aqua;
    }

    public float igni() {
        return igni;
    }

    public float aura() {
        return aura;
    }

    public float firmo() {
        return firmo;
    }

    public void setAqua(float aqua) {
        this.aqua = aqua;
    }

    public void setIgni(float igni) {
        this.igni = igni;
    }

    public void setAura(float aura) {
        this.aura = aura;
    }

    public void setFirmo(float firmo) {
        this.firmo = firmo;
    }

    public float sum() {
        return aqua + igni + aura + firmo;
    }

    public void scale(float factor) {
        aqua *= factor;
        igni *= factor;
        aura *= factor;
        firmo *= factor;
    }

    public void adjust(Element element, float delta) {
        if (delta == 0.0f || element == null) {
            return;
        }
        switch (element) {
            case AQUA -> aqua += delta;
            case IGNI -> igni += delta;
            case AURA -> aura += delta;
            case FIRMO -> firmo += delta;
        }
    }
}
