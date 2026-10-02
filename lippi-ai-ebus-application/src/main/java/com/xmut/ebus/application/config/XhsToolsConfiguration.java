package com.xmut.ebus.application.config;

import com.xmut.ebus.extension.tool.sku.ApifyOkHttpTransport;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ModelXhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteFetchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteFetchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteReranker;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearcher;
import com.xmut.lims.pi.ai.model.ModelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Xiaohongshu note Handler and Port/Searcher. Schema comes from {@code tools/xhs/*.tool.json}.
 */
@Configuration
@EnableConfigurationProperties({XhsNoteSearchProperties.class, XhsNoteFetchProperties.class})
public class XhsToolsConfiguration {

    @Bean
    public XhsNoteSearchPort xhsNoteSearchPort(XhsNoteSearchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockXhsNoteSearchClient();
        }
        return new ApifyXhsNoteSearchClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public XhsNoteReranker xhsNoteReranker(ObjectProvider<ModelProvider> models, XhsNoteSearchProperties props) {
        ModelProvider mp = models.getIfAvailable();
        if (mp == null) {
            return XhsNoteReranker.identity();
        }
        return new ModelXhsNoteReranker(mp, props);
    }

    @Bean
    public XhsNoteSearcher xhsNoteSearcher(XhsNoteSearchPort port, XhsNoteSearchProperties props, XhsNoteReranker reranker) {
        return new XhsNoteSearcher(port, props, reranker);
    }

    @Bean
    public XhsNoteFetchPort xhsNoteFetchPort(XhsNoteFetchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockXhsNoteFetchClient();
        }
        return new ApifyXhsNoteFetchClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public SearchXhsNoteToolHandler searchXhsNoteToolHandler(XhsNoteSearcher xhsNoteSearcher) {
        return new SearchXhsNoteToolHandler(xhsNoteSearcher);
    }

    @Bean
    public FetchXhsNoteToolHandler fetchXhsNoteToolHandler(XhsNoteFetchPort xhsNoteFetchPort) {
        return new FetchXhsNoteToolHandler(xhsNoteFetchPort);
    }
}
