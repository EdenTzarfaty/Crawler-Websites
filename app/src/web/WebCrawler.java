package web;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebCrawler {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";
	private static final Pattern HTML_LINK_PATTERN = Pattern.compile("href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
		if (args.length != 4) {
			System.err.println("Usage: java WebCrawler <URL> <max_urls> <depth> <uniqueness>");
			//System.exit(1);
		}

		String startUrl = args[0];
		
		if(!isValidUrl(startUrl)) {
			System.out.println("Please enter valid Url");
			return;
		}

		int maxUrls = Integer.parseInt(args[1]);
		int depth = Integer.parseInt(args[2]);
		boolean uniqueness = Boolean.parseBoolean(args[3]);

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();

		crawl(client, startUrl, 0, maxUrls, depth, uniqueness); // Starting with depth 0
		System.out.println("Crawling completed successfully.");
	}

	public static boolean isValidUrl(String urlString) throws MalformedURLException {
	    try {
	        URL url = new URL(urlString);
	        url.toURI();
	        return true;
	    } catch (URISyntaxException e) {
	        return false;
	    }
	}


	/**
	 * Crawl a website recursively up to a specified maximum depth and save HTML content to files.
	 *
	 * @param client       The HttpClient to use for making requests.
	 * @param url          The starting URL to crawl.
	 * @param currentDepth The current depth level of the crawling process.
	 * @param maxUrls      The maximum number of URLs to extract and process at each depth level.
	 * @param maxDepth     The maximum depth level to crawl.
	 * @param uniqueness   A flag indicating whether to filter out duplicate URLs.
	 * @throws IOException          If an I/O error occurs.
	 * @throws URISyntaxException   If the URL has an invalid syntax.
	 * @throws InterruptedException If the thread is interrupted while waiting for a response.
	 */
	private static void crawl(HttpClient client, String url, int currentDepth, int maxUrls, int maxDepth, boolean uniqueness) throws IOException, URISyntaxException, InterruptedException {

		String newUrl = checkUrl(url);

		URI uri = new URI(newUrl);
		if(uri.getHost() == null) {
			return;
		}

		// Create directory for current depth level
		Path depthDir = Path.of(Integer.toString(currentDepth));
		Files.createDirectories(depthDir);

		// Fetch the HTML content of the URL
		String html = fetchHtml(client, newUrl);

		// Save the HTML content to a file
		String fileName = replaceToUnderscore(newUrl);
		Path filePath = depthDir.resolve(replaceChar(fileName) + ".html");
		PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath, StandardOpenOption.CREATE)); 
		writer.write(html); // Write the content to the file

		// Extract URLs from the HTML
		Set<String> extractedUrls = extractUrls(html);

		// Filter out duplicate URLs if uniqueness is True
		if (uniqueness) {
			Set<String> uniqueUrls = new HashSet<>(extractedUrls);
			uniqueUrls.removeAll(getExistingUrls(currentDepth - 1));
			extractedUrls = uniqueUrls;
		}

		// Limit the number of extracted URLs
		if (extractedUrls.size() > maxUrls) {
			extractedUrls = limitUrls(extractedUrls, maxUrls);
		}

		// Process the extracted URLs recursively up to the maximum depth
		if (currentDepth < maxDepth) {
			for (String extractedUrl : extractedUrls) {
				crawl(client, extractedUrl, currentDepth + 1, maxUrls, maxDepth, uniqueness);
			}
		}	
	}

	/**
	 * This method takes in a URL and checks if it is a valid URL. If the URL is valid, it returns
	 * the same URL. If the URL is not valid, it replaces any characters not allowed for file names
	 * with an underscore.
	 *
	 * @param url The URL to be checked and modified (if needed).
	 * @return The valid URL with any illegal characters replaced with an underscore.
	 */

	private static String checkUrl(String url) {
		// Check if the URL starts with "http://" or "https://", and remove it if it does.
		int index1 = url.indexOf("//");
		if (index1 == 0) {
			url = url.substring(2);
		}

		// Check if the URL starts with "www.", and remove it if it does.
		int index2 = url.indexOf("www.");
		if (index2 != -1) {
			url = url.substring(index2);
		} else if(index2 == -1) {
			url = "www." + url;
		}

		// Check if the URL starts with "//", and add "https:" to the beginning if it does.
		if(url.startsWith("//")) {
			url = "https:" + url;
		}

		// Check if the URL starts with "https://", and add it if it doesn't.
		if(!url.startsWith("https://")) {
			url = "https://" + url;
		}
		return url;
	}

	/**
	 * Fetch the HTML content from a given URL using an HttpClient.
	 *
	 * @param client The HttpClient to use for making the request.
	 * @param url    The URL from which to fetch the HTML content.
	 * @return The fetched HTML content as a string.
	 * @throws IOException          If an I/O error occurs.
	 * @throws InterruptedException If the thread is interrupted while waiting for the response.
	 */
	private static String fetchHtml(HttpClient client, String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder() // HttpRequest builder
				.uri(URI.create(url)) // Set the URI of the request to the specified URL
				.header("User-Agent", USER_AGENT) // Set the "User-Agent" header to a user agent string
				.build(); // Build the HttpRequest object

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString()); // Send the request and get the response as a string
		return response.body(); // Return the body of the response (the fetched HTML content) as a string
	}

	/**
	 * Replaces all occurrences of the matched characters with an underscore.
	 *
	 * @param url - The URL from which to generate the file name.
	 * @return file name with underscore
	 */
	private static String replaceToUnderscore(String url) { 
		return url.replaceAll("[^a-zA-Z0-9.-]", "_");
	}

	/**

	 * Replaces "https://" or "http://" with an empty string and replaces forward slashes with underscores.
	 * Additionally replaces dots with underscores.
	 * @param url - the URL to modify
	 * @return the modified URL with replaced characters
	 */
	public static String replaceChar(String url) {
		// Remove "https://" or "http://"
		String output = url.replaceAll("^https?://", "");
		output.replace("/", "_");
		// Replace dots with underscores
		return output.replace(".", "_");
	}



	/**
	 * Extract URLs from an HTML string.
	 *
	 * @param html - The HTML string from which to extract URLs.
	 * @return A set of extracted URLs.
	 */
	private static Set<String> extractUrls(String html) {
		Set<String> urls = new HashSet<>(); // HashSet to store the extracted URLs
		Matcher matcher = HTML_LINK_PATTERN.matcher(html); // Create a Matcher object by applying the HTML_LINK_PATTERN to the input HTML string

		while (matcher.find()) { // Find all matches of the pattern in the HTML string
			String url = matcher.group(1); // Get the matched URL from the first capturing group of the pattern
			urls.add(url); 
		}
		return urls; // Return the urls set containing the extracted URLs
	}

	/**
	 * Retrieve existing URLs from files in a directory corresponding to the given depth.
	 *
	 * @param depth - The depth for which to retrieve existing URLs.
	 * @return A set of existing URLs.
	 * @throws IOException If an I/O error occurs.
	 */
	private static Set<String> getExistingUrls(int depth) throws IOException {
		Set<String> existingUrls = new HashSet<>(); // HashSet to store the existing URLs
		Path depthDirectory = Path.of(Integer.toString(depth)); // A path for the directory corresponding to the given depth

		if (Files.exists(depthDirectory)) { // Check if the directory for the given depth exists
			try (var stream = Files.newDirectoryStream(depthDirectory, "*.html")) { // Open a directory stream for files with the ".html" extension 
				for (Path file : stream) { // Iterate over each file in the directory stream
					String fileName = file.getFileName().toString(); // Get the file name as a string
					existingUrls.add(fileName); 
				}
			}
		}

		return existingUrls; // Return the existingUrls set
	}


	/**
	 * Limit the number of URLs in a set and return a new set with the limited URLs.
	 *
	 * @param urls - Set of URLs to be limited.
	 * @param limit - The maximum number of URLs to include in the limited set.
	 * @return A new set containing the limited URLs.
	 */
	private static Set<String> limitUrls(Set<String> urls, int limitOfUrl) {
		Set<String> limitedUrls = new HashSet<>(); //HashSet to store the limited URLs
		int countUrl = 0; //count to keep track of the number of URLs added

		for (String url : urls) { // Iterate over each URL in the input set
			limitedUrls.add(url); 
			countUrl++; 

			if (countUrl >= limitOfUrl) { // Check if the count exceeds the specified limit
				break; 
			}
		}

		return limitedUrls; 
	}
}
