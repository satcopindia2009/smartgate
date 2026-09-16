import { useEffect, useMemo, useRef, useState } from "react";
import type { Gate } from "../lib/types";

interface Props {
  gates: Gate[];
  selected: string[];
  onChange: (ids: string[]) => void;
}

export function GateMultiSelect({ gates, selected, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const allIds = useMemo(() => gates.map((g) => g.id), [gates]);
  const allSelected = selected.length === 0 || selected.length === allIds.length;

  const label = useMemo(() => {
    if (allSelected) return "All gates";
    if (selected.length === 1) {
      return gates.find((g) => g.id === selected[0])?.name || "1 gate";
    }
    return `${selected.length} gates`;
  }, [allSelected, selected, gates]);

  function toggle(id: string) {
    // From "All gates", picking one gate filters to that gate only.
    if (allSelected) {
      onChange([id]);
      return;
    }
    const next = selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id];
    onChange(next.length === 0 || next.length === allIds.length ? [] : next);
  }

  function selectAll() {
    onChange([]);
  }

  return (
    <div className={`gate-multi ${open ? "open" : ""}`} ref={ref}>
      <button
        type="button"
        className="gate-multi-btn"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="Filter by gate (multi-select)"
        onClick={() => setOpen((v) => !v)}
      >
        <span>{label}</span>
        <span className="caret" aria-hidden>
          ▾
        </span>
      </button>
      {open && (
        <div className="gate-multi-menu" role="listbox" aria-multiselectable="true">
          <label className="gate-multi-option">
            <input type="checkbox" checked={allSelected} onChange={selectAll} />
            All gates
          </label>
          {gates.map((g) => {
            const checked = allSelected || selected.includes(g.id);
            return (
              <label key={g.id} className="gate-multi-option">
                <input type="checkbox" checked={checked} onChange={() => toggle(g.id)} />
                {g.name}
              </label>
            );
          })}
        </div>
      )}
    </div>
  );
}
