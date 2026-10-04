# MiniLM-Edge：面向边缘设备的句向量模型

MiniLM-Edge 是 Example Labs 在 2026 年初开源的一组句嵌入模型，目标是在 **ARM 与低功耗 x86** 上跑通检索与聚类，而不是追求云端 SOTA 榜单。

## 设计目标

1. **参数量 ≤ 80M**，INT8 量化后模型文件小于 40MB。  
2. **延迟**：在 Raspberry Pi 5 上单句编码 < 120ms（batch=1，官方实测）。  
3. **许可证**：Apache-2.0，可商用。

团队说明：该系列不替代云端 7B 级 reranker，适合设备端缓存、本地 FAQ 匹配与表单去重。

## 架构

骨干为 6 层 MiniLM，隐藏维度 384。训练数据混合了 MS MARCO 子集、开源代码搜索对，以及 200 万条合成 paraphrase。

```python
from miniml_edge import MiniLMEdgeEncoder

enc = MiniLMEdgeEncoder.from_pretrained("example/miniml-edge-base-int8")
vec = enc.encode("边缘侧向量检索示例")
assert vec.shape == (384,)
```

导出格式支持 ONNX 与 GGUF（仅嵌入头），文档建议边缘部署优先 ONNX Runtime Mobile。

## 评测（摘录）

在 BEIR 子集 `nfcorpus` 上，MiniLM-Edge-Base 的 nDCG@10 为 **0.312**，低于同尺寸云端模型约 4 个点；在 **设备功耗 < 5W** 约束下，团队认为 trade-off 可接受。

原文未提供中文检索集的独立分数。

## 使用限制

- 不支持多模态输入；图像需先经其它模型转文本。  
- 最大序列长度 256 token，超长文本需客户端分句再平均池化。  
- 不提供医疗、法律或投资建议；示例代码仅作技术演示。

## 获取方式

权重与卡片见 Hugging Face：`example/miniml-edge-base-int8`。  
Issue 与讨论：https://github.com/example/miniml-edge
