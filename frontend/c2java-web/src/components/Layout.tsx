import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { 
  Home, 
  Upload, 
  List, 
  Settings,
  Code,
  LogOut,
  User
} from 'lucide-react';
import { useAuthStore } from '../store/authStore';

const navigation = [
  { name: '대시보드', href: '/', icon: Home },
  { name: '파일 업로드', href: '/upload', icon: Upload },
  { name: '작업 목록', href: '/jobs', icon: List },
  { name: '환경설정', href: '/admin', icon: Settings, adminOnly: true },
];

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // 관리자 메뉴 필터링
  const filteredNavigation = navigation.filter(item => {
    if (item.adminOnly) {
      return user?.role === 'ADMIN' || user?.role === 'MANAGER';
    }
    return true;
  });

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Sidebar */}
      <div className="fixed inset-y-0 left-0 z-50 w-64 bg-gray-900 flex flex-col">
        <div className="flex h-16 shrink-0 items-center px-6">
          <Code className="h-8 w-8 text-indigo-500" />
          <span className="ml-2 text-xl font-bold text-white">C2JAVA</span>
        </div>
        
        <nav className="flex-1 px-4 py-4">
          <ul className="flex flex-col gap-y-2">
            {filteredNavigation.map((item) => {
              const isActive = location.pathname === item.href;
              return (
                <li key={item.name}>
                  <Link
                    to={item.href}
                    className={`
                      group flex gap-x-3 rounded-md p-3 text-sm font-semibold leading-6
                      ${isActive 
                        ? 'bg-gray-800 text-white' 
                        : 'text-gray-400 hover:text-white hover:bg-gray-800'}
                    `}
                  >
                    <item.icon className="h-6 w-6 shrink-0" aria-hidden="true" />
                    {item.name}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* 사용자 정보 & 로그아웃 */}
        <div className="border-t border-gray-800 p-4">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-full bg-indigo-600 flex items-center justify-center">
              <User className="h-5 w-5 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate">
                {user?.displayName || user?.username}
              </p>
              <p className="text-xs text-gray-400 truncate">
                {user?.role === 'ADMIN' ? '관리자' : user?.role === 'MANAGER' ? '매니저' : '사용자'}
              </p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-400 hover:text-white hover:bg-gray-800 rounded-md transition-colors"
          >
            <LogOut className="h-5 w-5" />
            로그아웃
          </button>
        </div>
      </div>

      {/* Main content */}
      <div className="pl-64">
        <main className="py-6 px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
