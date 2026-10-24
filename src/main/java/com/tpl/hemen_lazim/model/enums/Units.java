package com.tpl.hemen_lazim.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Units {
    PARCA("parça"), ADET("adet"), PAKET("paket"), KG("kg"), LITRE("litre"), KUTU("kutu"), DIGER("diğer");

    // Bildirim metninde cümle içinde geçen ad: "Birisi 2 kg un ..."
    private final String label;
}
