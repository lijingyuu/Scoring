package com.scoring.backend.service.match;

import cn.hutool.json.JSONObject;
import com.scoring.backend.common.JsonUtils;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 战报封存前置校验（ensureReportComplete）。
 * 重点回归 2026-10-13 修复：排球小程序链路只写 meta 根节点 matchTimeText（lineup 赛前
 * 填报）+ 4 个签名，签名区 matchDateText 恒为空，封存校验须回退读取根级 matchTimeText，
 * 否则排球战报永远无法封存。
 */
@ExtendWith(MockitoExtension.class)
class MatchReportAssemblerTest {

    @Mock
    private MatchReportMetaMapper matchReportMetaMapper;

    private MatchReportAssembler newAssembler() {
        return new MatchReportAssembler(matchReportMetaMapper);
    }

    private JSONObject baseSignatures() {
        JSONObject root = new JSONObject();
        JSONObject signatures = new JSONObject();
        signatures.set("leftParticipant", "张三");
        signatures.set("rightParticipant", "李四");
        root.set("reportSignatures", signatures);
        return root;
    }

    /** 排球链路：双队长 + 主裁 + 副裁签名齐全，日期只在根级 matchTimeText —— 修复前会被拒绝 */
    @Test
    void volleyballChainPassesWithRootLevelMatchTimeTextFallback() {
        JSONObject root = baseSignatures();
        root.getJSONObject("reportSignatures").set("chiefReferee", "王裁");
        root.getJSONObject("reportSignatures").set("assistantReferee", "刘裁");
        root.set("matchTimeText", "2026-10-13 14:00");

        assertDoesNotThrow(() -> newAssembler().ensureReportComplete(root));
    }

    /** 羽毛球链路：日期写在签名区 matchDateText —— 原有行为保持 */
    @Test
    void badmintonChainPassesWithSignatureDate() {
        JSONObject root = baseSignatures();
        root.getJSONObject("reportSignatures").set("referee", "王裁");
        root.getJSONObject("reportSignatures").set("matchDateText", "2026-10-13");

        assertDoesNotThrow(() -> newAssembler().ensureReportComplete(root));
    }

    /** 签名齐但两处日期都缺 —— 仍然拒绝 */
    @Test
    void missingEveryDateIsRejected() {
        JSONObject root = baseSignatures();
        root.getJSONObject("reportSignatures").set("chiefReferee", "王裁");
        root.getJSONObject("reportSignatures").set("assistantReferee", "刘裁");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newAssembler().ensureReportComplete(root));
        assertEquals("战报签名和日期未填写完整", ex.getMessage());
    }

    /** 签名不齐 —— 拒绝（即使根级有时间） */
    @Test
    void missingSignaturesIsRejectedEvenWithRootTime() {
        JSONObject root = baseSignatures();
        root.set("matchTimeText", "2026-10-13 14:00");

        assertThrows(IllegalArgumentException.class,
                () -> newAssembler().ensureReportComplete(root));
    }

    /** buildReportMetaJson 落盘链路自洽：lineup 提交的 matchTimeText 存在根节点而非签名区 */
    @Test
    void buildReportMetaJsonKeepsMatchTimeTextAtRoot() {
        MatchReportAssembler assembler = newAssembler();
        JSONObject current = JsonUtils.parseObject("{}");
        String json = assembler.buildReportMetaJson(
                cn.hutool.core.bean.BeanUtil.copyProperties(
                        java.util.Map.of("matchTimeText", "2026-10-13 14:00",
                                "chiefRefereeName", "王裁",
                                "assistantRefereeName", "刘裁"),
                        com.scoring.backend.domain.dto.SaveMatchReportMetaReq.class),
                current);
        JSONObject root = JsonUtils.parseObject(json);
        assertEquals("2026-10-13 14:00", root.getStr("matchTimeText"));
        assertEquals("", root.getJSONObject("reportSignatures").getStr("matchDateText", ""));
    }
}
