package com.xmut.forma.pi.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IterationBudgetTest {

    @Test
    void maxTotal_less_than_one_rejected() {
        assertThatThrownBy(() -> new IterationBudget(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotal");
        assertThatThrownBy(() -> new IterationBudget(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consume_until_exhausted_returns_false() {
        IterationBudget budget = new IterationBudget(2);
        assertThat(budget.consume()).isTrue();
        assertThat(budget.used()).isEqualTo(1);
        assertThat(budget.remaining()).isEqualTo(1);

        assertThat(budget.consume()).isTrue();
        assertThat(budget.used()).isEqualTo(2);
        assertThat(budget.remaining()).isEqualTo(0);

        assertThat(budget.consume()).isFalse();
        assertThat(budget.used()).isEqualTo(2);
    }

    @Test
    void refund_does_not_make_used_negative() {
        IterationBudget budget = new IterationBudget(3);
        budget.refund();
        assertThat(budget.used()).isEqualTo(0);

        assertThat(budget.consume()).isTrue();
        assertThat(budget.used()).isEqualTo(1);
        budget.refund();
        assertThat(budget.used()).isEqualTo(0);
        budget.refund();
        assertThat(budget.used()).isEqualTo(0);
        assertThat(budget.remaining()).isEqualTo(3);
        assertThat(budget.maxTotal()).isEqualTo(3);
    }
}
