import { getApiBaseUrl } from './http';

// const buildStreamUrl = (path, params = {}) => {
//   const apiBaseUrl = getApiBaseUrl().replace(/\/$/, '');
//   const normalizedPath = path.startsWith('/') ? path : `/${path}`;
//   const url = new URL(`${apiBaseUrl}${normalizedPath}`, window.location.origin);
//
//   Object.entries(params).forEach(([key, value]) => {
//     if (value !== undefined && value !== null && value !== '') {
//       url.searchParams.set(key, value);
//     }
//   });
//
//   return url.toString();
// };
// #NEW CODE#
export const buildStreamUrl = (path, params = {}) => {
  const apiBaseUrl = getApiBaseUrl().replace(/\/$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${apiBaseUrl}${normalizedPath}`, window.location.origin);

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, value);
    }
  });

  return url.toString();
};

// export const openChatStream = ({ path, message, chatId }) => {
//   const url = buildStreamUrl(path, {
//     message,
//     chatId,
//   });
//
//   return new EventSource(url);
// };
// #NEW CODE#
export const openChatStream = ({ path, message, chatId, responseLength }) => {
  const url = buildStreamUrl(path, {
    message,
    chatId,
    responseLength,
  });

  return new EventSource(url);
};
