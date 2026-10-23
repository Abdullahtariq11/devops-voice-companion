import { useEffect, useRef, useState } from 'react'

type RecognitionResultEvent = {
  results: ArrayLike<{
    isFinal: boolean
    [index: number]: { transcript: string }
  }>
}

type BrowserRecognition = {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((event: RecognitionResultEvent) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start: () => void
  abort: () => void
}

type RecognitionConstructor = new () => BrowserRecognition

function getRecognitionConstructor() {
  if (typeof window === 'undefined') return undefined

  const browser = window as Window & {
    SpeechRecognition?: RecognitionConstructor
    webkitSpeechRecognition?: RecognitionConstructor
  }

  return browser.SpeechRecognition ?? browser.webkitSpeechRecognition
}

function recognitionErrorMessage(error: string) {
  switch (error) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'Microphone access was denied. Allow it in your browser settings, or use Check build status.'
    case 'audio-capture':
      return 'No microphone is available. Check your microphone or use Check build status.'
    case 'network':
      return 'The speech recognition service could not be reached. Check your internet connection or use the button.'
    case 'no-speech':
      return 'No speech was detected. Tap the microphone and try again.'
    default:
      return 'Speech recognition stopped unexpectedly. Try again or use Check build status.'
  }
}

function getAvailableVoices() {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return []

  const available = window.speechSynthesis.getVoices()
  const english = available.filter((voice) => /^en(?:[-_]|$)/i.test(voice.lang))
  return english.length ? english : available
}

export function useBrowserSpeech(onTranscript: (transcript: string) => void) {
  const [isListening, setIsListening] = useState(false)
  const [isSpeaking, setIsSpeaking] = useState(false)
  const [speechError, setSpeechError] = useState('')
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>(getAvailableVoices)
  const [selectedVoiceURI, setSelectedVoiceURI] = useState<string | null>(null)
  const [rate, setRate] = useState(0.90)
  const recognitionRef = useRef<BrowserRecognition | null>(null)
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null)
  const supportsRecognition = Boolean(getRecognitionConstructor())
  const supportsSynthesis = typeof window !== 'undefined' && 'speechSynthesis' in window
  const selectedVoice = selectedVoiceURI === null
    ? voices.find((voice) => voice.name.toLowerCase() === 'google uk english female')
    : voices.find((voice) => voice.voiceURI === selectedVoiceURI)

  useEffect(() => {
    if (!('speechSynthesis' in window)) return

    function refreshVoices() {
      setVoices(getAvailableVoices())
    }

    const synthesis = window.speechSynthesis
    synthesis.addEventListener('voiceschanged', refreshVoices)
    const timer = window.setTimeout(refreshVoices, 0)

    return () => {
      window.clearTimeout(timer)
      synthesis.removeEventListener('voiceschanged', refreshVoices)
    }
  }, [])

  function cancelRecognition() {
    const recognition = recognitionRef.current
    recognitionRef.current = null
    if (recognition) {
      recognition.onresult = null
      recognition.onerror = null
      recognition.onend = null
      recognition.abort()
    }
  }

  function cancelUtterance() {
    if (utteranceRef.current) {
      utteranceRef.current.onend = null
      utteranceRef.current.onerror = null
      utteranceRef.current = null
      window.speechSynthesis.cancel()
    }
  }

  useEffect(() => {
    return () => {
      cancelRecognition()
      cancelUtterance()
    }
  }, [])

  function stopListening() {
    cancelRecognition()
    setIsListening(false)
  }

  function stopSpeaking() {
    cancelUtterance()
    setIsSpeaking(false)
  }

  function changeVoice(voiceURI: string) {
    stopSpeaking()
    setSelectedVoiceURI(voiceURI)
  }

  function changeRate(value: number) {
    if (!Number.isFinite(value)) return
    stopSpeaking()
    setRate(Math.min(1.25, Math.max(0.75, value)))
  }

  function startListening() {
    const Recognition = getRecognitionConstructor()

    if (!Recognition) {
      setSpeechError('Speech recognition is not supported in this browser. Use Check build status instead.')
      return
    }

    stopSpeaking()
    stopListening()
    setSpeechError('')

    const recognition = new Recognition()
    recognition.lang = 'en-US'
    recognition.continuous = false
    recognition.interimResults = false
    recognitionRef.current = recognition

    recognition.onresult = (event) => {
      if (recognitionRef.current !== recognition) return

      const transcript = Array.from(event.results)
        .filter((result) => result.isFinal)
        .map((result) => result[0]?.transcript ?? '')
        .join(' ')
        .trim()

      if (!transcript) return

      stopListening()
      onTranscript(transcript)
    }

    recognition.onerror = (event) => {
      if (recognitionRef.current !== recognition) return
      stopListening()
      if (event.error !== 'aborted') {
        setSpeechError(recognitionErrorMessage(event.error))
      }
    }

    recognition.onend = () => {
      if (recognitionRef.current === recognition) {
        recognitionRef.current = null
        setIsListening(false)
      }
    }

    try {
      recognition.start()
      setIsListening(true)
    } catch {
      stopListening()
      setSpeechError('Could not start the microphone. Try again or use Check build status.')
    }
  }

  function speak(text: string) {
    stopListening()
    stopSpeaking()
    setSpeechError('')

    if (!supportsSynthesis) {
      setSpeechError('Spoken playback is unavailable in this browser. Your answer is still visible in Conversation.')
      return
    }

    const utterance = new SpeechSynthesisUtterance(text)
    if (selectedVoice) utterance.voice = selectedVoice
    utterance.lang = selectedVoice?.lang ?? 'en-US'
    utterance.rate = rate
    utteranceRef.current = utterance

    utterance.onend = () => {
      if (utteranceRef.current !== utterance) return
      utteranceRef.current = null
      setIsSpeaking(false)
    }

    utterance.onerror = () => {
      if (utteranceRef.current !== utterance) return
      utteranceRef.current = null
      setIsSpeaking(false)
      setSpeechError('Audio playback failed. Your answer is still visible; select Read aloud to try again.')
    }

    try {
      setIsSpeaking(true)
      window.speechSynthesis.speak(utterance)
    } catch {
      stopSpeaking()
      setSpeechError('Could not play the answer. You can still read it in Conversation.')
    }
  }

  return {
    isListening,
    isSpeaking,
    speechError,
    supportsRecognition,
    supportsSynthesis,
    voices,
    selectedVoiceURI: selectedVoice?.voiceURI ?? '',
    rate,
    changeVoice,
    changeRate,
    startListening,
    stopListening,
    stopSpeaking,
    speak,
  }
}
