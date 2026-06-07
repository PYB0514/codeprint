// 후원 엔티티 식별자 Value Object
package com.codeprint.domain.donation;

import java.util.UUID;

public record DonationId(UUID value) {
    public static DonationId of(UUID value) {
        return new DonationId(value);
    }
}
