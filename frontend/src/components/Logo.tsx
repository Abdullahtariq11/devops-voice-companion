export default function Logo() {
  return (
    <div className="flex size-12 items-center justify-center rounded-xl border border-primary/20 bg-primary/10">
      <svg
        aria-hidden="true"
        viewBox="0 0 32 32"
        fill="none"
        className="size-8 text-primary"
      >
        <path
          d="M3 16h5l3-8 5 16 5-20 3 12h5"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  )
}
