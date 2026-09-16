import type { SVGProps } from "react";

function Svg(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" className="icon" aria-hidden="true" {...props}>
      {props.children}
    </svg>
  );
}

export function IconLive() {
  return (
    <Svg>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
    </Svg>
  );
}

export function IconHistory() {
  return (
    <Svg>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 6v6l4 2" />
    </Svg>
  );
}

export function IconBlacklist() {
  return (
    <Svg>
      <circle cx="12" cy="12" r="10" />
      <path d="M4.93 4.93l14.14 14.14" />
    </Svg>
  );
}

export function IconGates() {
  return (
    <Svg>
      <rect x="3" y="11" width="18" height="11" rx="2" />
      <path d="M7 11V7a5 5 0 0110 0v4" />
    </Svg>
  );
}

export function IconReports() {
  return (
    <Svg>
      <path d="M18 20V10M12 20V4M6 20v-6" />
    </Svg>
  );
}

export function IconSearch() {
  return (
    <Svg>
      <circle cx="11" cy="11" r="8" />
      <path d="M21 21l-4.35-4.35" />
    </Svg>
  );
}

export function IconExport() {
  return (
    <Svg>
      <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3" />
    </Svg>
  );
}

export function IconShield() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </svg>
  );
}
