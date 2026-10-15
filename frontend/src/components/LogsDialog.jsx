import { useEffect, useRef, useState } from "react";
import { api } from "../api.js";

export default function LogsDialog({ appId, pod, onClose }) {
  const [text, setText] = useState("Loading…");
  const ref = useRef(null);

  useEffect(() => {
    ref.current?.showModal();
    api(`/apps/${appId}/pods/${pod}/logs?tail=300`)
      .then((r) => setText(r.text || "This pod hasn't written any logs yet."))
      .catch((e) => setText(e.message));
  }, [appId, pod]);

  return (
    <dialog ref={ref} className="logs-dialog" onClose={onClose}>
      <header>
        <h2>
          Logs for <code>{pod}</code>
        </h2>
        <button className="btn btn-quiet" onClick={() => ref.current?.close()}>
          Close
        </button>
      </header>
      <pre>{text}</pre>
    </dialog>
  );
}
