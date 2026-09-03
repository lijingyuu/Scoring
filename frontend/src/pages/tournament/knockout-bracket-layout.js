export const KNOCKOUT_BRACKET_LAYOUT = {
  cardWidth: 320,
  cardHeight: 128,
  columnGap: 75,
  boardPaddingX: 18,
  firstMatchTop: 88,
  firstRoundGap: 64,
  thirdPlaceGap: 56,
}

export const KNOCKOUT_BRACKET_VIEWPORT = {
  minScale: 0.1,
  maxScale: 0.98,
  baseScale: 0.7,
  scaleStep: 0.105,
  padding: 24,
}

export function clampBracketScale(scale) {
  return clampBracketScaleWithin(scale, KNOCKOUT_BRACKET_VIEWPORT.minScale, KNOCKOUT_BRACKET_VIEWPORT.maxScale)
}

export function clampBracketScaleWithin(scale, minScale, maxScale) {
  const value = Number(scale)
  if (!Number.isFinite(value)) return 1
  return Math.max(minScale, Math.min(maxScale, value))
}

export function calculateOverviewScale(layoutSize, viewportSize) {
  const layoutWidth = Number(layoutSize?.width || 0)
  const viewportWidth = Number(viewportSize?.width || 0)
  if (layoutWidth <= 0 || viewportWidth <= 0) return 1

  return Math.max(KNOCKOUT_BRACKET_VIEWPORT.minScale, viewportWidth / layoutWidth)
}

export function calculateFitScale(layoutSize, viewportSize) {
  const layoutWidth = Number(layoutSize?.width || 0)
  const layoutHeight = Number(layoutSize?.height || 0)
  const viewportWidth = Number(viewportSize?.width || 0)
  const viewportHeight = Number(viewportSize?.height || 0)
  if (layoutWidth <= 0 || layoutHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1

  const availableWidth = Math.max(1, viewportWidth - KNOCKOUT_BRACKET_VIEWPORT.padding * 2)
  const availableHeight = Math.max(1, viewportHeight - KNOCKOUT_BRACKET_VIEWPORT.padding * 2)
  return clampBracketScaleWithin(Math.min(availableWidth / layoutWidth, availableHeight / layoutHeight), KNOCKOUT_BRACKET_VIEWPORT.minScale, KNOCKOUT_BRACKET_VIEWPORT.maxScale)
}

export function centerBracketPosition(layoutSize, viewportSize, scale) {
  const scaledWidth = Number(layoutSize?.width || 0) * scale
  const scaledHeight = Number(layoutSize?.height || 0) * scale
  const viewportWidth = Number(viewportSize?.width || 0)
  const viewportHeight = Number(viewportSize?.height || 0)
  return {
    x: (viewportWidth - scaledWidth) / 2,
    y: (viewportHeight - scaledHeight) / 2,
  }
}

export function keepViewportCenterPosition(layoutSize, viewportSize, previous, nextScale) {
  const oldScale = normalizeScale(previous?.scale)
  const scale = normalizeScale(nextScale)
  const viewportCenterX = Number(viewportSize?.width || 0) / 2
  const viewportCenterY = Number(viewportSize?.height || 0) / 2
  const contentCenterX = (viewportCenterX - Number(previous?.x || 0)) / oldScale
  const contentCenterY = (viewportCenterY - Number(previous?.y || 0)) / oldScale
  const x = viewportCenterX - contentCenterX * scale
  const y = viewportCenterY - contentCenterY * scale
  return clampBracketPosition(layoutSize, viewportSize, { x, y }, scale)
}

export function clampBracketPosition(layoutSize, viewportSize, position, scale) {
  return {
    x: clampAxisPosition(Number(position?.x || 0), Number(layoutSize?.width || 0) * scale, Number(viewportSize?.width || 0)),
    y: clampAxisPosition(Number(position?.y || 0), Number(layoutSize?.height || 0) * scale, Number(viewportSize?.height || 0)),
  }
}

function normalizeScale(value) {
  const scale = Number(value)
  return Number.isFinite(scale) && scale > 0 ? scale : 1
}

function clampAxisPosition(position, contentSize, viewportSize) {
  if (contentSize <= 0 || viewportSize <= 0) return position
  if (contentSize <= viewportSize) return (viewportSize - contentSize) / 2
  return Math.min(0, Math.max(viewportSize - contentSize, position))
}

function getMatchId(match) {
  return String(match?.id ?? match?.matchId ?? match?.match_id ?? '')
}

function getRoundNum(match) {
  return Number(match?.roundNum ?? match?.round_num ?? 0)
}

function getMatchIndex(match) {
  return Number(match?.matchIndex ?? match?.match_index ?? 0)
}

function getNextMatchId(match) {
  return String(match?.nextMatchId ?? match?.next_match_id ?? '')
}

function isThirdPlaceMatch(match) {
  return Number(match?.matchRole ?? match?.match_role ?? 0) === 1
}

function sortMatches(matches) {
  return [...matches].sort((left, right) => getMatchIndex(left) - getMatchIndex(right))
}

function sourceNodesFor(target, previousNodes) {
  const directSources = previousNodes.filter((node) => getNextMatchId(node.match) === target.id)
  if (directSources.length) return directSources

  const targetIndex = getMatchIndex(target.match)
  return previousNodes.filter((node) => Math.floor(getMatchIndex(node.match) / 2) === targetIndex)
}

function fallbackCenter(matchOrder, roundOrder) {
  const firstCenter = KNOCKOUT_BRACKET_LAYOUT.firstMatchTop + KNOCKOUT_BRACKET_LAYOUT.cardHeight / 2
  const firstRoundStep = KNOCKOUT_BRACKET_LAYOUT.cardHeight + KNOCKOUT_BRACKET_LAYOUT.firstRoundGap
  const sourceSpan = 2 ** roundOrder
  return firstCenter + (matchOrder * sourceSpan + (sourceSpan - 1) / 2) * firstRoundStep
}

function addConnectorSegments(target, sourceNodes, segments) {
  if (!sourceNodes.length) return

  const targetCenter = target.center
  if (sourceNodes.length === 1) {
    const source = sourceNodes[0]
    segments.push({
      id: `${source.id}-${target.id}-direct`,
      axis: 'horizontal',
      left: source.left + KNOCKOUT_BRACKET_LAYOUT.cardWidth,
      top: source.center,
      width: target.left - source.left - KNOCKOUT_BRACKET_LAYOUT.cardWidth,
    })
    return
  }

  const sortedSources = [...sourceNodes].sort((left, right) => left.center - right.center)
  const branchX = target.left - KNOCKOUT_BRACKET_LAYOUT.columnGap / 2
  const firstSource = sortedSources[0]
  const lastSource = sortedSources[sortedSources.length - 1]

  for (const source of sortedSources) {
    segments.push({
      id: `${source.id}-${target.id}-source`,
      axis: 'horizontal',
      left: source.left + KNOCKOUT_BRACKET_LAYOUT.cardWidth,
      top: source.center,
      width: branchX - source.left - KNOCKOUT_BRACKET_LAYOUT.cardWidth,
    })
  }

  segments.push({
    id: `${target.id}-branch`,
    axis: 'vertical',
    left: branchX,
    top: firstSource.center,
    height: lastSource.center - firstSource.center,
  })
  segments.push({
    id: `${target.id}-target`,
    axis: 'horizontal',
    left: branchX,
    top: targetCenter,
    width: target.left - branchX,
  })
}

export function buildKnockoutBracketLayout(matches) {
  const normalMatches = (Array.isArray(matches) ? matches : []).filter((match) => (
    getRoundNum(match) > 0 && !isThirdPlaceMatch(match)
  ))
  const thirdPlaceMatch = (Array.isArray(matches) ? matches : []).find(isThirdPlaceMatch)
  const roundsByNumber = new Map()

  for (const match of normalMatches) {
    const roundNum = getRoundNum(match)
    if (!roundsByNumber.has(roundNum)) roundsByNumber.set(roundNum, [])
    roundsByNumber.get(roundNum).push(match)
  }

  const rounds = [...roundsByNumber.entries()]
    .sort(([left], [right]) => left - right)
    .map(([roundNum, roundMatches], columnIndex) => ({
      roundNum,
      columnIndex,
      left: KNOCKOUT_BRACKET_LAYOUT.boardPaddingX + columnIndex * (KNOCKOUT_BRACKET_LAYOUT.cardWidth + KNOCKOUT_BRACKET_LAYOUT.columnGap),
      matches: sortMatches(roundMatches),
    }))

  const nodes = []
  const nodesByRound = new Map()

  for (const round of rounds) {
    const previousRound = rounds[round.columnIndex - 1]
    const previousNodes = previousRound ? nodesByRound.get(previousRound.roundNum) || [] : []
    const roundNodes = round.matches.map((match, matchOrder) => {
      const sourceNodes = sourceNodesFor({ id: getMatchId(match), match }, previousNodes)
      const center = sourceNodes.length
        ? sourceNodes.reduce((total, source) => total + source.center, 0) / sourceNodes.length
        : fallbackCenter(matchOrder, round.columnIndex)
      return {
        id: getMatchId(match),
        match,
        roundNum: round.roundNum,
        left: round.left,
        top: center - KNOCKOUT_BRACKET_LAYOUT.cardHeight / 2,
        center,
      }
    })

    nodes.push(...roundNodes)
    nodesByRound.set(round.roundNum, roundNodes)
  }

  const finalNode = [...nodes].reverse().find((node) => !getNextMatchId(node.match)) || nodes[nodes.length - 1]
  if (thirdPlaceMatch && finalNode) {
    const top = finalNode.top + KNOCKOUT_BRACKET_LAYOUT.cardHeight + KNOCKOUT_BRACKET_LAYOUT.thirdPlaceGap
    nodes.push({
      id: getMatchId(thirdPlaceMatch),
      match: thirdPlaceMatch,
      roundNum: finalNode.roundNum,
      left: finalNode.left,
      top,
      center: top + KNOCKOUT_BRACKET_LAYOUT.cardHeight / 2,
    })
  }

  const segments = []
  for (const round of rounds.slice(1)) {
    const previousRound = rounds[round.columnIndex - 1]
    const previousNodes = nodesByRound.get(previousRound.roundNum) || []
    const currentNodes = nodesByRound.get(round.roundNum) || []
    for (const target of currentNodes) {
      addConnectorSegments(target, sourceNodesFor(target, previousNodes), segments)
    }
  }

  const maxRight = Math.max(0, ...nodes.map((node) => node.left + KNOCKOUT_BRACKET_LAYOUT.cardWidth))
  const maxBottom = Math.max(
    KNOCKOUT_BRACKET_LAYOUT.firstMatchTop + KNOCKOUT_BRACKET_LAYOUT.cardHeight,
    ...nodes.map((node) => node.top + KNOCKOUT_BRACKET_LAYOUT.cardHeight),
  )
  const firstRoundNodes = rounds.length
    ? nodesByRound.get(rounds[0].roundNum) || []
    : []
  const firstRoundHeight = Math.max(
    0,
    ...firstRoundNodes.map((node) => node.top + KNOCKOUT_BRACKET_LAYOUT.cardHeight),
  )

  return {
    width: maxRight + KNOCKOUT_BRACKET_LAYOUT.boardPaddingX,
    height: maxBottom + 24,
    firstRoundHeight,
    rounds,
    nodes,
    segments,
  }
}

export function toRpxStyle(values) {
  return Object.fromEntries(
    Object.entries(values)
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => [key, `${value}rpx`]),
  )
}
