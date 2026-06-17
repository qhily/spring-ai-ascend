package com.bank.financial.research.agent;

import com.bank.financial.research.calc.RevenueImpactModel;
import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Sector / macro specialist. Two jobs, both writing to the blackboard so the
 * writer narrates from a single shared source:
 *
 * <ol>
 *   <li><b>Quantify external information.</b> Each news item becomes a driver whose
 *       direction is its sentiment and whose magnitude is a bounded first-order
 *       shock; a driver-based model decomposes the revenue/EPS impact. The
 *       sentiment→shock mapping is an explicit documented heuristic (a desk would
 *       substitute calibrated elasticities), but the flow-through is real.</li>
 *   <li><b>Compose the macro and industry digests.</b> The macro indicators and the
 *       competitive/landscape material (peers, demand signals, transcript colour)
 *       are folded into two compact digests on the blackboard, which become the
 *       dedicated macro and industry sections' writing material.</li>
 * </ol>
 */
public final class SectorMacroAgent implements ReportSubAgent {

    private static final double SHOCK_SCALE = 0.10;     // |sentiment|=1 ⇒ ±10% driver move
    private static final double REVENUE_ELASTICITY = 0.5; // revenue %-change per 1% driver move

    @Override
    public String role() {
        return "sector-macro";
    }

    @Override
    public String capability() {
        return "sector-macro";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        writeMacroDigest(ctx, ds);
        writeIndustryDigest(ctx, ds);
        quantifyExternalImpact(ctx, ds);
    }

    /** Fold the macro indicators into one digest the macro section narrates from. */
    private void writeMacroDigest(ReportContext ctx, CompanyData.Dataset ds) {
        StringBuilder sb = new StringBuilder();
        if (ds.macro().isEmpty()) {
            sb.append("(本次未接入宏观指标,以下从行业与公司层面展开)");
        } else {
            for (CompanyData.MacroIndicator m : ds.macro()) {
                sb.append(m.name()).append(" ").append(Bb.fmt(m.value())).append(m.unit())
                        .append("(来源:").append(m.provenance().source()).append(");");
            }
        }
        ctx.put(role(), Bb.MACRO_DIGEST, sb.toString());
    }

    /** Fold peers, demand signals and transcript colour into one industry digest. */
    private void writeIndustryDigest(ReportContext ctx, CompanyData.Dataset ds) {
        StringBuilder sb = new StringBuilder();
        if (ds.hasPeers()) {
            CompanyData.PeerSet p = ds.peers();
            sb.append("可比公司中位:EV/EBITDA ").append(Bb.fmt(p.evEbitda()))
                    .append("、EV/Sales ").append(Bb.fmt(p.evSales()))
                    .append("、P/E ").append(Bb.fmt(p.priceEarnings()))
                    .append("(对标 ").append(String.join("、", p.peers())).append(");");
        }
        if (ds.hasFundamentals()) {
            sb.append("公司营收规模 ").append(Bb.fmt(ds.fundamentals().revenue()))
                    .append(" ").append(ds.fundamentals().currency()).append("(百万);");
        }
        for (CompanyData.TextItem n : ds.news()) {
            sb.append("【资讯】").append(n.title()).append(":").append(n.body()).append(";");
        }
        for (CompanyData.TextItem t : ds.transcriptHighlights()) {
            sb.append("【业绩会】").append(t.title()).append(":").append(t.body()).append(";");
        }
        if (sb.length() == 0) {
            sb.append("(本次未接入行业可比与外部资讯,行业判断以公司基本面为主)");
        }
        ctx.put(role(), Bb.INDUSTRY_DIGEST, sb.toString());
    }

    /** Decompose news into a revenue/EPS impact when both fundamentals and news exist. */
    private void quantifyExternalImpact(ReportContext ctx, CompanyData.Dataset ds) {
        if (!ds.hasFundamentals() || ds.news().isEmpty()) {
            return;
        }
        CompanyData.Fundamentals f = ds.fundamentals();
        List<RevenueImpactModel.Driver> drivers = new ArrayList<>();
        for (CompanyData.TextItem item : ds.news()) {
            double shock = item.sentiment() * SHOCK_SCALE;
            if (shock != 0) {
                drivers.add(new RevenueImpactModel.Driver(item.title(), REVENUE_ELASTICITY, shock));
            }
        }
        if (drivers.isEmpty()) {
            return;
        }
        RevenueImpactModel.ImpactResult impact = RevenueImpactModel.analyze(
                f.revenue(), f.incrementalMargin(), f.taxRate(), f.dilutedShares(), drivers);
        ctx.putNum(role(), Bb.REVENUE_IMPACT_PCT, impact.deltaRevenuePct());
        ctx.putNum(role(), Bb.EPS_IMPACT, impact.epsImpact());
    }
}
