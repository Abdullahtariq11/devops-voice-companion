export function isBuildStatusQuestion(transcript: string) {
  const question = transcript.toLowerCase().replace(/[’‘]/g, "'")

  if (/\b(why|logs?|summari[sz]e|cause|failed runs|recent failures)\b/.test(question)) {
    return false
  }

  return /\b(build|pipeline|workflow|ci)\b/.test(question) &&
    /\b(how|status|check|latest|passing|passed|running|healthy|success)\b/.test(question)
}
