package com.bank.financial.research.agent;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import com.bank.financial.research.model.ReportModel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single writer — one voice for the whole report (the STORM / earnings-call
 * pattern of a sole drafter to preserve coherence). For each outline section it
 * assembles a fact-bearing brief from the blackboard's canonical figures and asks
 * the model to turn it into prose; the figures are passed verbatim so the prose
 * stays anchored to the computed numbers. The same {@link #writeSection} is used
 * for the initial draft and for critic-driven revisions (each revision appends a
 * new version, preserving the audit trail via the blackboard's append log).
 */
public final class WriterAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(ReportContext ctx) {
        String outline = ctx.latest(Bb.OUTLINE).orElse(PlannerAgent.OUTLINE);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    /** Draft (or revise) one section; returns the body and writes it to the blackboard. */
    public String writeSection(ReportContext ctx, String id) {
        String title = titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            try {
                body = ctx.model().generate(new ReportModel.ModelTask(
                        "writer", com.bank.financial.research.model.WriterPrompts.section(title, wordsFor(id), anchorFor(id)),
                        brief, 900));
            } catch (RuntimeException e) {
                // Live model failed/timed out — degrade this section to facts so the
                // report stays complete instead of aborting the whole run.
                ctx.degraded("writer:" + id, e.getMessage());
                body = "(本节模型生成失败,降级为事实摘要)\n" + brief;
            }
        } else {
            // Budget exhausted — emit a faithful fact-only section so the report stays complete.
            ctx.degraded("writer:" + id, "model budget exhausted");
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), Bb.SECTION_PREFIX + id, body);
        return body;
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与评级";
            case "macro" -> "宏观经济环境";
            case "industry" -> "行业格局与竞争态势";
            case "thesis" -> "投资论点";
            case "model" -> "盈利预测与模型";
            case "valuation" -> "估值";
            case "scenario" -> "情景与风险";
            case "sector" -> "行业、宏观与外部信息影响";
            default -> id;
        };
    }

    /** Soft length target per section — the analytical sections run longer. */
    private static int wordsFor(String id) {
        return switch (id) {
            case "summary" -> 400;
            case "macro", "industry", "thesis", "scenario" -> 600;
            default -> 500;
        };
    }

    /** What each section must link its conclusion back to. */
    private static String anchorFor(String id) {
        return switch (id) {
            case "macro" -> "公司所处需求与利润率环境,并为后续行业与盈利判断铺垫";
            case "industry" -> "公司的竞争位置与盈利能力,并指向投资论点";
            case "valuation", "model", "scenario" -> "总体投资评级与目标价";
            default -> "总体投资论点与评级";
        };
    }

    private String briefFor(ReportContext ctx, String id) {
        Map<String, String> facts = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                facts.put("公司", ctx.latest(Bb.COMPANY).orElse(ctx.request().ticker()));
                facts.put("评级", ctx.latest(Bb.RATING).orElse("n/a"));
                facts.put("目标价", ctx.latest(Bb.PRICE_TARGET).orElse("n/a"));
                facts.put("现价", ctx.latest(Bb.CURRENT_PRICE).orElse("n/a"));
                facts.put("潜在空间(%)", ctx.latest(Bb.UPSIDE_PCT).map(v -> Bb.pct(parse(v))).orElse("n/a"));
                facts.put("收敛判定", ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("n/a"));
            }
            case "macro" -> {
                facts.put("宏观指标", ctx.latest(Bb.MACRO_DIGEST).orElse("n/a"));
                facts.put("货币与利率环境", ctx.latest(Bb.MACRO_DIGEST).isPresent()
                        ? "结合上列指标判断需求与融资成本的方向" : "n/a");
                facts.put("对本公司的传导", "宏观环境如何影响本公司的需求端与利润率(定性,不新增数字)");
            }
            case "industry" -> {
                facts.put("行业与竞争", ctx.latest(Bb.INDUSTRY_DIGEST).orElse("n/a"));
                facts.put("外部信息收入影响", ctx.latest(Bb.REVENUE_IMPACT_PCT)
                        .map(v -> Bb.pct(parse(v))).orElse("n/a"));
                facts.put("外部信息EPS影响", ctx.latest(Bb.EPS_IMPACT).orElse("n/a"));
            }
            case "thesis" -> {
                facts.put("投资论点", ctx.latest(Bb.THESIS).orElse(""));
                facts.put("收入趋势收敛", ctx.latest(Bb.TREND_CONVERGENT).orElse("n/a"));
                facts.put("盈利惊喜分类", ctx.latest(Bb.SUE_CLASS).orElse("n/a"));
            }
            case "model" -> {
                facts.put("FY1收入", ctx.latest(Bb.REVENUE_FY1).orElse("n/a"));
                facts.put("FY1每股收益", ctx.latest(Bb.EPS_FY1).orElse("n/a"));
                facts.put("FY1自由现金流", ctx.latest(Bb.FCF_FY1).orElse("n/a"));
                facts.put("隐含增速(%)", ctx.latest(Bb.GROWTH).map(v -> Bb.pct(parse(v))).orElse("n/a"));
                facts.put("SUE", ctx.latest(Bb.SUE).orElse("n/a"));
            }
            case "valuation" -> {
                facts.put("DCF每股", ctx.latest(Bb.DCF_PER_SHARE).orElse("n/a"));
                facts.put("终值占比", ctx.latest(Bb.DCF_TERMINAL_WEIGHT).orElse("n/a"));
                facts.put("可比中位每股", ctx.latest(Bb.COMPS_MEDIAN).orElse("n/a"));
                facts.put("可比区间", ctx.latest(Bb.COMPS_LOW).orElse("n/a") + " ~ " + ctx.latest(Bb.COMPS_HIGH).orElse("n/a"));
                facts.put("收敛判定", ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("n/a"));
                facts.put("收敛后每股", ctx.latest(Bb.CONVERGENCE_BLENDED).orElse("n/a"));
                facts.put("方法离散度", ctx.latest(Bb.CONVERGENCE_DISPERSION).orElse("n/a"));
                facts.put("WACC", ctx.latest("valuation.wacc").orElse("n/a"));
                facts.put("永续增长", ctx.latest("valuation.terminalGrowth").orElse("n/a"));
            }
            case "scenario" -> {
                facts.put("乐观每股", ctx.latest(Bb.SCENARIO_BULL).orElse("n/a"));
                facts.put("中性每股", ctx.latest(Bb.SCENARIO_BASE).orElse("n/a"));
                facts.put("悲观每股", ctx.latest(Bb.SCENARIO_BEAR).orElse("n/a"));
                facts.put("概率加权期望每股", ctx.latest(Bb.SCENARIO_EXPECTED).orElse("n/a"));
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
