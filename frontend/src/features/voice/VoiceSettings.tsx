type VoiceSettingsProps = {
  voices: SpeechSynthesisVoice[]
  selectedVoiceURI: string
  rate: number
  disabled: boolean
  isSpeaking: boolean
  onVoiceChange: (voiceURI: string) => void
  onRateChange: (rate: number) => void
  onPreview: () => void
  onStop: () => void
}

export default function VoiceSettings({
  voices,
  selectedVoiceURI,
  rate,
  disabled,
  isSpeaking,
  onVoiceChange,
  onRateChange,
  onPreview,
  onStop,
}: VoiceSettingsProps) {
  return (
    <details className="relative mt-4 rounded-xl border border-border bg-inset">
      <summary className="cursor-pointer rounded-xl px-4 py-3 text-xs font-medium text-muted transition-colors duration-200 hover:bg-surface-hover hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transition-none">
        Voice &amp; playback
      </summary>
      <div className="space-y-4 px-4 pt-1 pb-4">
        <div>
          <label htmlFor="playback-voice" className="block text-xs font-medium">Voice</label>
          <select
            id="playback-voice"
            value={selectedVoiceURI}
            disabled={disabled}
            onChange={(event) => onVoiceChange(event.target.value)}
            aria-describedby="playback-voice-help"
            className="mt-2 min-h-11 w-full rounded-lg border border-border bg-surface px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40"
          >
            <option value="">Browser default</option>
            {voices.map((voice) => (
              <option key={voice.voiceURI} value={voice.voiceURI}>
                {voice.name} · {voice.lang}{voice.default ? ' · default' : ''}
              </option>
            ))}
          </select>
          <p id="playback-voice-help" className="mt-2 text-xs leading-relaxed text-muted">
            {voices.length ? 'Try different voices to find your preferred sound. Availability depends on your browser and device.' : 'No named voices are available yet. Preview uses the browser default.'}
          </p>
        </div>

        <div>
          <label htmlFor="playback-speed" className="flex justify-between text-xs font-medium">
            Speaking speed
            <span className="font-mono text-steel tabular-nums">{rate.toFixed(2)}×</span>
          </label>
          <input
            id="playback-speed"
            type="range"
            min="0.75"
            max="1.25"
            step="0.05"
            value={rate}
            disabled={disabled}
            onChange={(event) => onRateChange(event.target.valueAsNumber)}
            aria-valuetext={`${rate.toFixed(2)} times normal speed`}
            className="mt-2 min-h-8 w-full accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40"
          />
        </div>

        <button
          type="button"
          disabled={disabled}
          onClick={isSpeaking ? onStop : onPreview}
          className="min-h-11 w-full rounded-full border border-primary/20 px-4 text-xs font-medium text-primary-light transition-colors duration-200 hover:bg-primary/10 active:bg-primary/20 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
        >
          {isSpeaking ? 'Stop playback' : 'Preview voice'}
        </button>
      </div>
    </details>
  )
}
