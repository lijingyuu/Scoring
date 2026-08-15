const SIGNATURE_RESULT_EVENT_PREFIX = 'signature-result:'

export function createSignatureEventKey(target = 'signature') {
  const suffix = Math.random().toString(36).slice(2, 10)
  return `${Date.now()}-${target}-${suffix}`
}

export function buildSignatureResultEvent(eventKey) {
  return SIGNATURE_RESULT_EVENT_PREFIX + eventKey
}

export function buildSignatureCaptureUrl({ eventKey = '', label = '' } = {}) {
  return '/pages/signature/index?eventKey='
    + encodeURIComponent(eventKey)
    + '&label='
    + encodeURIComponent(label)
}
