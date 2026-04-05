import { ApolloClient, InMemoryCache, createHttpLink, from } from "@apollo/client";
import { onError } from "@apollo/client/link/error";

const httpLink = createHttpLink({
	uri: "/graphql",
});

const errorLink = onError(({ graphQLErrors, networkError }) => {
	if (graphQLErrors) {
		for (const { message, locations, path } of graphQLErrors) {
			console.error(`[GraphQL error]: Message: ${message}, Path: ${String(path)}`);
			if (locations) {
				console.error("Locations:", locations);
			}
		}
	}
	if (networkError) {
		console.error(`[Network error]: ${networkError.message}`);
	}
});

export const apolloClient = new ApolloClient({
	link: from([errorLink, httpLink]),
	cache: new InMemoryCache(),
	defaultOptions: {
		watchQuery: {
			errorPolicy: "all",
		},
		query: {
			errorPolicy: "all",
		},
	},
});
