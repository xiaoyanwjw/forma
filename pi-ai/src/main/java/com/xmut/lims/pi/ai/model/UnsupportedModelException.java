package com.xmut.lims.pi.ai.model;

/**
 * Catalog 未注册用例。
 */
public class UnsupportedModelException extends RuntimeException {

    private final String useCase;

    public UnsupportedModelException(String useCase) {
        super("Unsupported model useCase: " + useCase);
        this.useCase = useCase;
    }

    public String getUseCase() {
        return useCase;
    }
}
