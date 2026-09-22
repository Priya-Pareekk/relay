import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider, useMutation, useQueryClient } from '@tanstack/react-query';
import { Sidebar } from '@/components/layout/Sidebar';
import { DispatchTicker } from '@/components/layout/DispatchTicker';
import { ToastProvider, useToast } from '@/components/ui/Toast';
import { DashboardView } from '@/features/dashboard/DashboardView';
import { MainQueueView } from '@/features/queue/MainQueueView';
import { JobDetailView } from '@/features/job-detail/JobDetailView';
import { JobDefinitionsView } from '@/features/definitions/JobDefinitionsView';
import { DeadLettersView } from '@/features/dead-letters/DeadLettersView';
import { SubmitJobModal } from '@/features/queue/SubmitJobModal';
import { api } from '@/api/client';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 2000,
    },
  },
});

const AppLayout: React.FC = () => {
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
  const clientQuery = useQueryClient();
  const { showToast } = useToast();

  // Demo seeding mutation
  const seedMutation = useMutation({
    mutationFn: () => api.seedDemoData(35),
    onSuccess: (res) => {
      clientQuery.invalidateQueries({ queryKey: ['recent-jobs'] });
      clientQuery.invalidateQueries({ queryKey: ['recent-jobs-ticker'] });
      clientQuery.invalidateQueries({ queryKey: ['jobs'] });
      clientQuery.invalidateQueries({ queryKey: ['dead-letters'] });
      clientQuery.invalidateQueries({ queryKey: ['metrics-summary'] });
      showToast(
        'success',
        'DEMO LEDGER SEEDED',
        `Generated ${res?.count || 35} synthetic signals with realistic failures & retries.`
      );
    },
    onError: (err: any) => {
      showToast('error', 'SEEDING FAILED', err.message);
    },
  });

  return (
    <div className="min-h-screen bg-relay-bg text-relay-text flex flex-row">
      {/* Fixed Left Sidebar Nav */}
      <Sidebar
        onOpenSubmitModal={() => setIsSubmitModalOpen(true)}
        onSeedData={() => seedMutation.mutate()}
        isSeeding={seedMutation.isPending}
      />

      {/* Main Right Content Panel */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Pinned Top Telegraph Dispatch Ticker */}
        <DispatchTicker />

        {/* Dynamic Route View */}
        <main className="flex-1 overflow-y-auto">
          <Routes>
            <Route
              path="/"
              element={
                <DashboardView
                  onOpenSubmitModal={() => setIsSubmitModalOpen(true)}
                />
              }
            />
            <Route
              path="/dashboard"
              element={
                <DashboardView
                  onOpenSubmitModal={() => setIsSubmitModalOpen(true)}
                />
              }
            />
            <Route path="/jobs" element={<MainQueueView />} />
            <Route path="/jobs/:id" element={<JobDetailView />} />
            <Route path="/definitions" element={<JobDefinitionsView />} />
            <Route path="/dead-letters" element={<DeadLettersView />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>

      {/* Dispatch New Job Modal */}
      <SubmitJobModal
        isOpen={isSubmitModalOpen}
        onClose={() => setIsSubmitModalOpen(false)}
      />
    </div>
  );
};

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <AppLayout />
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}

