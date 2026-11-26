import { X } from "lucide-react";
import type { ReactNode } from "react";

export default function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="spread">
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button className="ghost icon" onClick={onClose} aria-label="Close"><X size={20} aria-hidden /></button>
        </div>
        {children}
      </div>
    </div>
  );
}
