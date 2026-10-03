package com.xmut.forma.infrastructure.media;

import com.xmut.forma.domain.business.media.store.MediaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MediaStoreConfiguration {

    @Bean
    public MediaStore inMemoryMediaStore(Clock clock) {
        return new InMemoryMediaStore(clock);
    }
}
