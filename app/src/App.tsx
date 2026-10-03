import { Route } from 'react-router-dom';
import { IonApp, IonRouterOutlet } from '@ionic/react';
import { IonReactRouter } from '@ionic/react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LiveProvider } from './live/Live';
import { SessionProvider } from './session/Session';
import CommunitySetup from './pages/admin/CommunitySetup';
import DoerInbox from './pages/doer/Inbox';
import ModeratorQueue from './pages/moderator/Queue';
import PlaybookList from './pages/playbook/PlaybookList';
import PlaybookPage from './pages/playbook/PlaybookPage';
import CaseStatus from './pages/resident/CaseStatus';
import HomeMap from './pages/resident/HomeMap';
import Ideas from './pages/resident/Ideas';
import MyReports from './pages/resident/MyReports';
import NewReport from './pages/resident/NewReport';
import Profile from './pages/resident/Profile';
import ReviewReport from './pages/resident/ReviewReport';
import SignIn from './pages/SignIn';
import Start from './pages/Start';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 10_000, retry: 1, refetchOnWindowFocus: true },
  },
});

/**
 * Path routing (/case/42, not /#/case/42). Ionic reads every hash change it did not make as "back",
 * so a pasted or typed hash link showed the previous page; with paths, those are full page loads.
 * The backend (SpaRouting) and Capacitor's local server both answer unknown paths with index.html.
 */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <IonApp>
          <LiveProvider>
            <IonReactRouter>
              <IonRouterOutlet>
                <Route path="/" element={<Start />} />
                <Route path="/signin" element={<SignIn />} />
                <Route path="/home" element={<HomeMap />} />
                <Route path="/report/new" element={<NewReport />} />
                <Route path="/report/:id" element={<ReviewReport />} />
                <Route path="/case/:id" element={<CaseStatus />} />
                <Route path="/my" element={<MyReports />} />
                <Route path="/ideas" element={<Ideas />} />
                <Route path="/profile" element={<Profile />} />
                <Route path="/doer" element={<DoerInbox />} />
                <Route path="/moderation" element={<ModeratorQueue />} />
                <Route path="/playbooks" element={<PlaybookList />} />
                <Route path="/playbooks/:ref" element={<PlaybookPage />} />
                <Route path="/admin" element={<CommunitySetup />} />
              </IonRouterOutlet>
            </IonReactRouter>
          </LiveProvider>
        </IonApp>
      </SessionProvider>
    </QueryClientProvider>
  );
}
