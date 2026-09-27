package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.media.support.ListingMediaMountSupport;
import org.springframework.stereotype.Component;

/**
 * Listing（persistAs=sku）：settle 前挂系统占位主图。
 */
@Component
public class SkuMediaMountInterceptor implements BilledRunInterceptor {

    private final ListingMediaMountSupport listingMediaMountSupport;

    public SkuMediaMountInterceptor(ListingMediaMountSupport listingMediaMountSupport) {
        this.listingMediaMountSupport = listingMediaMountSupport;
    }

    @Override
    public void after(BilledRunContext ctx) {
        if (!SkillRunProfile.PERSIST_SKU.equals(ctx.getProfile().getPersistAs())) {
            return;
        }

        ListingMediaMountSupport.MountedListingMedia mounted =
                listingMediaMountSupport.mountSystemPlaceholder(
                        ctx.getRun().getUserId(),
                        ctx.getProjectedView(),
                        ctx.getBusinessPayload());
        ctx.setProjectedView(mounted.getView());
        ctx.setBusinessPayload(mounted.getBusinessPayload());
    }
}
