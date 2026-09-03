/**
 * 排球记分恢复（Recovery）纯函数集合。
 *
 * 从 lineup.vue 提取，用于：加载赛事详情/比赛记录后判断「是否需要恢复计分」、
 * 计算当前应恢复到的局数、由后端记录重建本地恢复缓存。
 * 所有函数均为纯函数，便于单元测试。
 *
 * record 形如后端 match detail 中返回的比赛对象：
 *   { scoreDisplay, gameScores, events, lineupSnapshots, status,
 *     bestOf, pointsToWin, decidingPointsToWin, enableDeuce, capPoint,
 *     leftGameWins, rightGameWins, retiredSide, winnerSide, left, right }
 */

function parseScoreDisplay(scoreDisplay) {
  const match = String(scoreDisplay || "").match(/^(\d+):(\d+)$/);
  if (!match) return null;
  return {
    leftScore: Number(match[1] || 0),
    rightScore: Number(match[2] || 0),
  };
}

function isRecoveredGameWon(record, gameNo, score) {
  if (!score) return false;
  const capPoint = Number(record?.capPoint || 99);
  if (score.leftScore >= capPoint || score.rightScore >= capPoint) return true;
  const bestOf = Number(record?.bestOf || 3);
  const targetPoints = gameNo === bestOf && record?.decidingPointsToWin != null
    ? Number(record?.decidingPointsToWin || 15)
    : Number(record?.pointsToWin || 25);
  if (score.leftScore < targetPoints && score.rightScore < targetPoints) return false;
  return record?.enableDeuce === false
    ? score.leftScore !== score.rightScore
    : Math.abs(score.leftScore - score.rightScore) >= 2;
}

function findLatestScoredEvent(events, gameNo) {
  const targetGameNo = Number(gameNo || 0)
  return [...(Array.isArray(events) ? events : [])]
    .filter((item) => Number(item?.gameNo || 0) === targetGameNo)
    .filter((item) => Number(item?.leftScore || 0) > 0 || Number(item?.rightScore || 0) > 0)
    .sort((left, right) => Number(left?.eventSeq || 0) - Number(right?.eventSeq || 0))
    .pop() || null
}

function computeBackendRecovery(record) {
  const completedGameCount = Array.isArray(record?.gameScores) ? record.gameScores.length : 0;
  const events = Array.isArray(record?.events) ? record.events : [];
  const gameNos = [...new Set(events.map((item) => Number(item?.gameNo || 0)).filter((item) => item > 0))].sort((left, right) => left - right);
  const completedByEvents = gameNos.filter((gameNo) => {
    const gameEvents = events.filter((item) => Number(item?.gameNo || 0) === gameNo);
    const latestEvent = findLatestScoredEvent(gameEvents, gameNo);
    return isRecoveredGameWon(record, gameNo, latestEvent ? {
      leftScore: Number(latestEvent.leftScore || 0),
      rightScore: Number(latestEvent.rightScore || 0),
    } : null);
  });
  const completedCount = Math.max(completedGameCount, completedByEvents.length ? completedByEvents[completedByEvents.length - 1] : 0);
  const bestOf = Number(record?.bestOf || 3);
  const status = Number(record?.status || 0);
  if (status === 2 || status === 3) {
    return {
      currentGameNo: Math.max(1, completedCount),
      matchEnded: true,
    };
  }
  if (completedCount >= bestOf) {
    return {
      currentGameNo: bestOf,
      matchEnded: true,
    };
  }
  if (completedCount > 0) {
    return {
      currentGameNo: completedCount + 1,
      matchEnded: false,
    };
  }
  return {
    currentGameNo: 1,
    matchEnded: false,
  };
}

function findRecoveredGameScore(record, gameNo) {
  const targetGameNo = Number(gameNo || 0);
  if (targetGameNo <= 0) return null;
  const gameScores = Array.isArray(record?.gameScores) ? record.gameScores : [];
  const exactGameScore = [...gameScores].reverse().find((item) => Number(item?.gameNo || 0) === targetGameNo);
  if (exactGameScore) {
    return {
      leftScore: Number(exactGameScore.leftScore || 0),
      rightScore: Number(exactGameScore.rightScore || 0),
    };
  }

  const events = Array.isArray(record?.events) ? record.events : [];
  const latestEvent = findLatestScoredEvent(events, targetGameNo);
  if (latestEvent) {
    return {
      leftScore: Number(latestEvent.leftScore || 0),
      rightScore: Number(latestEvent.rightScore || 0),
    };
  }

  return null;
}

function collectRecoveredGameScores(record) {
  const scoresByGameNo = new Map();
  const sourceGameScores = Array.isArray(record?.gameScores) ? record.gameScores : [];
  sourceGameScores.forEach((item) => {
    const gameNo = Number(item?.gameNo || 0);
    if (gameNo <= 0) return;
    scoresByGameNo.set(gameNo, {
      gameNo,
      leftScore: Number(item?.leftScore || 0),
      rightScore: Number(item?.rightScore || 0),
      winnerSide: item?.winnerSide === 'left' || item?.winnerSide === 'right'
        ? item.winnerSide
        : Number(item?.leftScore || 0) > Number(item?.rightScore || 0)
          ? 'left'
          : 'right',
    });
  });

  const events = Array.isArray(record?.events) ? record.events : [];
  const gameNos = [...new Set(events.map((item) => Number(item?.gameNo || 0)).filter((item) => item > 0))].sort((left, right) => left - right);
  gameNos.forEach((gameNo) => {
    if (scoresByGameNo.has(gameNo)) return;
    const latestEvent = findLatestScoredEvent(events, gameNo);
    if (!latestEvent) return;
    const score = {
      leftScore: Number(latestEvent.leftScore || 0),
      rightScore: Number(latestEvent.rightScore || 0),
    };
    if (!isRecoveredGameWon(record, gameNo, score)) return;
    scoresByGameNo.set(gameNo, {
      gameNo,
      leftScore: score.leftScore,
      rightScore: score.rightScore,
      winnerSide: score.leftScore > score.rightScore ? 'left' : 'right',
    });
  });

  return [...scoresByGameNo.values()].sort((left, right) => Number(left.gameNo || 0) - Number(right.gameNo || 0));
}

function hasSavedProgress(record) {
  const gameScores = Array.isArray(record?.gameScores) ? record.gameScores : [];
  const lineupSnapshots = Array.isArray(record?.lineupSnapshots) ? record.lineupSnapshots : [];
  const events = Array.isArray(record?.events) ? record.events : [];
  if (Number(record?.status || 0) === 2 || Number(record?.status || 0) === 3) return false;
  if (gameScores.length > 0) return true;
  if (lineupSnapshots.length > 0) return true;
  if (String(record?.scoreDisplay || "").trim()) return true;
  return events.some((item) => {
    const type = String(item?.eventType || "");
    return Number(item?.gameNo || 0) > 0 && type && type !== "roster_snapshot";
  });
}

function hasScoreProgress(record, gameNo) {
  const gameScores = Array.isArray(record?.gameScores) ? record.gameScores : [];
  const events = Array.isArray(record?.events) ? record.events : [];
  if (Number(record?.status || 0) === 2 || Number(record?.status || 0) === 3) return false;
  const targetGameNo = Number(gameNo || 0);
  if (targetGameNo <= 0 && gameScores.length > 0) return true;
  if (targetGameNo <= 0 && String(record?.scoreDisplay || "").trim()) return true;
  return events.some((item) => {
    if (targetGameNo > 0 && Number(item?.gameNo || 0) !== targetGameNo) return false;
    return Number(item?.leftScore || 0) > 0 || Number(item?.rightScore || 0) > 0;
  });
}

function getMaxRecoveredEventSeq(record) {
  const events = Array.isArray(record?.events) ? record.events : [];
  return events.reduce((max, item) => {
    const seq = Number(item?.eventSeq || 0);
    return Number.isInteger(seq) && seq > max ? seq : max;
  }, 0);
}

function shouldUseLocalRecoveryCache(record, cached, sameSession) {
  if (!cached || !sameSession) return false
  const recovery = computeBackendRecovery(record)
  if (recovery.matchEnded) return false
  const eventGameNo = (Array.isArray(record?.events) ? record.events : [])
    .reduce((max, item) => Math.max(max, Number(item?.gameNo || 0)), 0)
  const lineupGameNo = (Array.isArray(record?.lineupSnapshots) ? record.lineupSnapshots : [])
    .reduce((max, item) => Math.max(max, Number(item?.gameNo || 0)), 0)
  const serverGameNo = Math.max(recovery.currentGameNo, eventGameNo, lineupGameNo)
  if (Number(cached.currentGameNo || 0) < serverGameNo) return false
  return getMaxRecoveredEventSeq(record) <= Number(cached.lastSyncedEventSeq || 0)
}

function findLatestRuntimeSnapshot(record, gameNo) {
  const targetGameNo = Number(gameNo || 0)
  const events = Array.isArray(record?.events) ? record.events : []
  const newestFirst = [...events]
    .sort((left, right) => Number(right?.eventSeq || 0) - Number(left?.eventSeq || 0))
  for (const item of newestFirst) {
    if (targetGameNo > 0 && Number(item?.gameNo || 0) !== targetGameNo) continue
    try {
      const payload = typeof item?.payloadJson === 'string' ? JSON.parse(item.payloadJson) : item?.payloadJson
      if (payload?.runtime && typeof payload.runtime === 'object') return payload.runtime
    } catch (_) {
      // Ignore malformed historical payloads and continue with score-only recovery.
    }
  }
  return null
}

function buildRecoveredMatchEvents(record) {
  return (Array.isArray(record?.events) ? record.events : []).map((item) => {
    let payload = {}
    try {
      payload = typeof item?.payloadJson === 'string'
        ? JSON.parse(item.payloadJson || '{}')
        : item?.payloadJson || {}
    } catch (_) {
      payload = {}
    }
    return {
      seq: Number(item?.eventSeq || 0),
      type: String(item?.eventType || ''),
      gameNo: Number(item?.gameNo || 1),
      leftScore: Number(item?.leftScore || 0),
      rightScore: Number(item?.rightScore || 0),
      serveSide: item?.serveSide === 'right' ? 'right' : 'left',
      payload,
      syncStatus: 'synced',
    }
  }).filter((item) => item.seq > 0 && item.type)
}

function shouldAutoResumeScoreboard(record, cached, gameNo, shouldUseCachedState, preventAutoResume = false) {
  if (preventAutoResume) return false;
  return (shouldUseCachedState && cached?.lineupReady)
    || hasScoreProgress(record, gameNo);
}

function computeRecoveredGameNo(record, cached) {
  const recovery = computeBackendRecovery(record);
  const cachedGameNo = Number(cached?.currentGameNo || 0);
  if (recovery.matchEnded) {
    return recovery.currentGameNo;
  }
  return Math.max(recovery.currentGameNo, cachedGameNo);
}

function buildRecoveredCacheFromRecord(record, requestedGameNo) {
  const score = findRecoveredGameScore(record, requestedGameNo) || parseScoreDisplay(record?.scoreDisplay) || { leftScore: 0, rightScore: 0 };
  const recoveredGameScores = collectRecoveredGameScores(record);
  const leftGameWins = recoveredGameScores.filter((item) => item.winnerSide === 'left').length;
  const rightGameWins = recoveredGameScores.filter((item) => item.winnerSide === 'right').length;
  const recovery = computeBackendRecovery(record);
  const fallbackWinnerSide = leftGameWins > rightGameWins
    ? 'left'
    : rightGameWins > leftGameWins
      ? 'right'
      : '';
  const runtime = findLatestRuntimeSnapshot(record, requestedGameNo)
  return {
    currentGameNo: Number(requestedGameNo || 1),
    leftScore: score.leftScore ?? 0,
    rightScore: score.rightScore ?? 0,
    leftGameWins: leftGameWins || Number(record?.leftGameWins || 0),
    rightGameWins: rightGameWins || Number(record?.rightGameWins || 0),
    gameScores: recoveredGameScores.length > 0
      ? recoveredGameScores.map((item) => ({ ...item }))
      : Array.isArray(record?.gameScores) ? record.gameScores.map((item) => ({ ...item })) : [],
    retiredSide: record?.retiredSide || "",
    matchEnded: recovery.matchEnded,
    winnerName: record?.winnerSide === "left"
      ? (record?.left?.name || "")
      : record?.winnerSide === "right"
        ? (record?.right?.name || "")
        : fallbackWinnerSide === "left"
          ? (record?.left?.name || "")
          : fallbackWinnerSide === "right"
            ? (record?.right?.name || "")
            : "",
    nextEventSeq: getMaxRecoveredEventSeq(record) + 1,
    lastSyncedEventSeq: getMaxRecoveredEventSeq(record),
    matchEvents: buildRecoveredMatchEvents(record),
    ...(runtime ? {
      runtimeRecovered: true,
      screenLeftParticipantSide: runtime.screenLeftParticipantSide,
      serveSide: runtime.serveSide,
      currentGameStartServeSide: runtime.currentGameStartServeSide,
      leftTimeouts: runtime.leftTimeouts,
      rightTimeouts: runtime.rightTimeouts,
      leftCourt: runtime.leftCourt,
      rightCourt: runtime.rightCourt,
      leftLiberoSetup: runtime.leftLiberoSetup,
      rightLiberoSetup: runtime.rightLiberoSetup,
      leftLiberoRuntime: runtime.leftLiberoRuntime,
      rightLiberoRuntime: runtime.rightLiberoRuntime,
      leftCaptainMemberId: runtime.leftCaptainMemberId,
      rightCaptainMemberId: runtime.rightCaptainMemberId,
      baseLeftCourt: runtime.baseLeftCourt || runtime.leftCourt,
      baseRightCourt: runtime.baseRightCourt || runtime.rightCourt,
      draftLeftCourt: runtime.leftCourt,
      draftRightCourt: runtime.rightCourt,
      draftServeSide: runtime.serveSide,
    } : {}),
  };
}

export {
  parseScoreDisplay,
  isRecoveredGameWon,
  computeBackendRecovery,
  findRecoveredGameScore,
  collectRecoveredGameScores,
  hasSavedProgress,
  hasScoreProgress,
  getMaxRecoveredEventSeq,
  shouldUseLocalRecoveryCache,
  findLatestRuntimeSnapshot,
  shouldAutoResumeScoreboard,
  computeRecoveredGameNo,
  buildRecoveredCacheFromRecord,
};
