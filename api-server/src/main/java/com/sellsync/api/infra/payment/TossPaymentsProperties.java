package com.sellsync.api.infra.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "toss")
public class TossPaymentsProperties {

    private String secretKey;
    private String clientKey;
    private String billingUrl = "https://api.tosspayments.com/v1/billing";
    private String paymentUrl = "https://api.tosspayments.com/v1/payments";
}
