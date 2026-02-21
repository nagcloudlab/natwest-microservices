interface Step {
  label: string;
  done: boolean;
  active: boolean;
}

export default function WorkflowStepper({ steps }: { steps: Step[] }) {
  return (
    <div className="flex items-center space-x-2 overflow-x-auto py-2">
      {steps.map((step, i) => (
        <div key={step.label} className="flex items-center">
          {i > 0 && (
            <div className={`w-8 h-0.5 ${step.done || step.active ? "bg-orange-400" : "bg-gray-300"}`} />
          )}
          <div className="flex flex-col items-center min-w-[80px]">
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
                step.done
                  ? "bg-green-500 text-white"
                  : step.active
                  ? "bg-orange-500 text-white ring-2 ring-orange-300"
                  : "bg-gray-200 text-gray-500"
              }`}
            >
              {step.done ? "\u2713" : i + 1}
            </div>
            <span className={`text-xs mt-1 text-center ${step.active ? "font-semibold text-orange-600" : "text-gray-500"}`}>
              {step.label}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
