import { useState } from "react";
import { REGIONS, USER_RANGES, APP_TYPES } from "../lib/questions";

const HA_CHOICES = [
  { label: "Yes, it must stay up", value: true },
  { label: "No, a little downtime is OK", value: false },
];

function Chips({ options, selected, onSelect, getValue = (o) => o.code }) {
  return (
    <div className="chip-row">
      {options.map((o) => {
        const value = getValue(o);
        return (
          <button
            key={o.label}
            type="button"
            className={`chip ${selected === value ? "chip--selected" : ""}`}
            aria-pressed={selected === value}
            onClick={() => onSelect(selected === value ? null : value)}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function Question({ icon, text, children }) {
  return (
    <div className="followup-question">
      <p className="followup-bubble"><span aria-hidden="true">{icon}</span> {text}</p>
      {children}
    </div>
  );
}

// Chat-style follow-up step: asks only what the description didn't answer.
// Every question is optional; unanswered ones are left to the AI estimate.
export default function FollowUpQuestions({ questions, onSubmit, onSkip }) {
  const [answers, setAnswers] = useState({});
  const [customUsers, setCustomUsers] = useState("");
  const set = (key) => (value) => setAnswers((a) => ({ ...a, [key]: value }));

  const submit = () => {
    const users = customUsers ? parseInt(customUsers, 10) : answers.users;
    onSubmit({ ...answers, users: users > 0 ? users : null });
  };

  return (
    <div className="card followup-card">
      <div className="card-header">
        <span className="card-tag">A FEW QUICK QUESTIONS</span>
        <span className="card-hint">Optional — sharpens the cost estimate</span>
      </div>

      {questions.includes("region") && (
        <Question icon="🌍" text="Where are most of your users?">
          <Chips options={REGIONS} selected={answers.region} onSelect={set("region")} />
        </Question>
      )}

      {questions.includes("users") && (
        <Question icon="👥" text="Roughly how many monthly users do you expect?">
          <Chips
            options={USER_RANGES}
            selected={customUsers ? null : answers.users}
            onSelect={(v) => { setCustomUsers(""); set("users")(v); }}
            getValue={(o) => o.value}
          />
          <input
            className="followup-input"
            type="number"
            min="1"
            inputMode="numeric"
            placeholder="or type an exact number"
            aria-label="Exact number of monthly users"
            value={customUsers}
            onChange={(e) => setCustomUsers(e.target.value)}
          />
        </Question>
      )}

      {questions.includes("appType") && (
        <Question icon="📱" text="What kind of app is it?">
          <Chips options={APP_TYPES} selected={answers.appType} onSelect={set("appType")} />
        </Question>
      )}

      {questions.includes("highAvailability") && (
        <Question icon="🛡️" text="Does it need to stay online if a server fails?">
          <Chips options={HA_CHOICES} selected={answers.highAvailability} onSelect={set("highAvailability")}
                 getValue={(o) => o.value} />
        </Question>
      )}

      <div className="followup-actions">
        <button type="button" className="submit-btn followup-submit" onClick={submit}>
          Get Estimate
        </button>
        <button type="button" className="clear-btn" onClick={onSkip}>
          Skip — let AI estimate
        </button>
      </div>
    </div>
  );
}
