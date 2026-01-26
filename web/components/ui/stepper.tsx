'use client';

import * as React from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

interface Step {
  id: number;
  title: string;
  description?: string;
}

interface StepperProps {
  steps: Step[];
  currentStep: number;
  onStepClick?: (step: number) => void;
  className?: string;
}

export function Stepper({ steps, currentStep, onStepClick, className }: StepperProps) {
  return (
    <div className={cn('w-full', className)}>
      <div className="flex items-center justify-between">
        {steps.map((step, index) => {
          const isCompleted = currentStep > step.id;
          const isCurrent = currentStep === step.id;
          
          return (
            <React.Fragment key={step.id}>
              <div className="flex flex-col items-center">
                <button
                  type="button"
                  onClick={() => onStepClick?.(step.id)}
                  disabled={!onStepClick || currentStep < step.id}
                  className={cn(
                    'w-10 h-10 rounded-full flex items-center justify-center text-sm font-medium transition-all',
                    isCompleted && 'bg-green-600 text-white',
                    isCurrent && 'bg-blue-600 text-white ring-4 ring-blue-100',
                    !isCompleted && !isCurrent && 'bg-gray-200 text-gray-500',
                    onStepClick && currentStep >= step.id && 'cursor-pointer hover:opacity-80'
                  )}
                >
                  {isCompleted ? <Check className="w-5 h-5" /> : step.id}
                </button>
                <div className="mt-2 text-center">
                  <p className={cn('text-sm font-medium', isCurrent ? 'text-blue-600' : 'text-gray-700')}>
                    {step.title}
                  </p>
                  {step.description && <p className="text-xs text-gray-500 mt-0.5">{step.description}</p>}
                </div>
              </div>
              {index < steps.length - 1 && (
                <div className={cn('flex-1 h-1 mx-4 rounded', currentStep > step.id ? 'bg-green-600' : 'bg-gray-200')} />
              )}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}
