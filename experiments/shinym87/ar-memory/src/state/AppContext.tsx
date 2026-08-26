import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { AppData, Comment, Post, User } from "../types";
import { loadAppData, saveAppData } from "../utils/storage";

interface NewPostInput {
  spotId: string;
  category: Post["category"];
  description: string;
  mediaType: Post["mediaType"];
  mediaUrl: string;
  visibility: Post["visibility"];
}

interface AppContextValue {
  users: User[];
  posts: Post[];
  currentUser: User;
  setCurrentUserId: (id: string) => void;
  addPost: (input: NewPostInput) => void;
  toggleLike: (postId: string) => void;
  addComment: (postId: string, text: string) => void;
  toggleNeighbor: (targetUserId: string) => void;
  getUser: (id: string) => User | undefined;
  /** 특정 지점의 게시물을 로그인한 계정 기준으로 나눠서 반환한다. */
  getSpotPosts: (spotId: string) => { neighborPosts: Post[]; morePosts: Post[] };
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<AppData>(() => loadAppData());

  useEffect(() => {
    saveAppData(data);
  }, [data]);

  const currentUser = useMemo(
    () => data.users.find((u) => u.id === data.currentUserId) ?? data.users[0],
    [data.users, data.currentUserId],
  );

  const setCurrentUserId = useCallback((id: string) => {
    setData((prev) => ({ ...prev, currentUserId: id }));
  }, []);

  const addPost = useCallback((input: NewPostInput) => {
    setData((prev) => {
      const newPost: Post = {
        id: `post-${Date.now()}`,
        authorId: prev.currentUserId,
        createdAt: new Date().toISOString(),
        likedBy: [],
        comments: [],
        ...input,
      };
      return { ...prev, posts: [newPost, ...prev.posts] };
    });
  }, []);

  const toggleLike = useCallback((postId: string) => {
    setData((prev) => ({
      ...prev,
      posts: prev.posts.map((p) => {
        if (p.id !== postId) return p;
        const liked = p.likedBy.includes(prev.currentUserId);
        return {
          ...p,
          likedBy: liked
            ? p.likedBy.filter((id) => id !== prev.currentUserId)
            : [...p.likedBy, prev.currentUserId],
        };
      }),
    }));
  }, []);

  const addComment = useCallback((postId: string, text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    setData((prev) => {
      const comment: Comment = {
        id: `cmt-${Date.now()}`,
        authorId: prev.currentUserId,
        text: trimmed,
        createdAt: new Date().toISOString(),
      };
      return {
        ...prev,
        posts: prev.posts.map((p) =>
          p.id === postId ? { ...p, comments: [...p.comments, comment] } : p,
        ),
      };
    });
  }, []);

  const toggleNeighbor = useCallback((targetUserId: string) => {
    setData((prev) => ({
      ...prev,
      users: prev.users.map((u) => {
        if (u.id !== prev.currentUserId) return u;
        const isNeighbor = u.neighbors.includes(targetUserId);
        return {
          ...u,
          neighbors: isNeighbor
            ? u.neighbors.filter((id) => id !== targetUserId)
            : [...u.neighbors, targetUserId],
        };
      }),
    }));
  }, []);

  const getUser = useCallback(
    (id: string) => data.users.find((u) => u.id === id),
    [data.users],
  );

  const getSpotPosts = useCallback(
    (spotId: string) => {
      const viewer = data.users.find((u) => u.id === data.currentUserId);
      const neighborIds = new Set(viewer?.neighbors ?? []);
      const byTimeAsc = (a: Post, b: Post) =>
        new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();

      const spotPosts = data.posts
        .filter((p) => p.spotId === spotId)
        .sort(byTimeAsc);

      const neighborPosts: Post[] = [];
      const morePosts: Post[] = [];

      for (const p of spotPosts) {
        const isMine = p.authorId === data.currentUserId;
        const isNeighborAuthor = neighborIds.has(p.authorId);

        if (isMine || isNeighborAuthor) {
          // 본인 글이거나 이웃의 글: 공개범위와 무관하게 기본 노출
          neighborPosts.push(p);
        } else if (p.visibility === "all") {
          // 이웃이 아닌 사람의 전체공개 글: "더보기"로 분리
          morePosts.push(p);
        }
        // 이웃이 아닌 사람의 "이웃 공개" 글은 노출하지 않음
      }

      return { neighborPosts, morePosts };
    },
    [data.posts, data.users, data.currentUserId],
  );

  const value: AppContextValue = {
    users: data.users,
    posts: data.posts,
    currentUser,
    setCurrentUserId,
    addPost,
    toggleLike,
    addComment,
    toggleNeighbor,
    getUser,
    getSpotPosts,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp은 AppProvider 내부에서만 사용할 수 있습니다.");
  return ctx;
}
