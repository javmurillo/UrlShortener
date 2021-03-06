package urlshortener.common.web;

import com.google.common.hash.Hashing;

import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import urlshortener.common.domain.ShortURL;
import urlshortener.common.repository.ClickRepository;
import urlshortener.common.repository.ShortURLRepository;
import urlshortener.common.domain.Click;

import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
public class UrlShortenerController {
	private static final Logger LOG = LoggerFactory
			.getLogger(UrlShortenerController.class);
	@Autowired
	protected ShortURLRepository shortURLRepository;

	@Autowired
	protected ClickRepository clickRepository;

	@RequestMapping(value = "/{id:(?!link).*}", method = RequestMethod.GET)
	public ResponseEntity<?> redirectTo(@PathVariable String id,
			HttpServletRequest request) {
		String hash = id.split("\\+")[0];
		ShortURL l = shortURLRepository.findByKey(hash);
		if (l != null) {
			shortURLRepository.incCount(l);
			System.out.println("COUNT: " + l.getCount());
			createAndSaveClick(id, extractIP(request));
			return createSuccessfulRedirectToResponse(l);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	private void createAndSaveClick(String hash, String ip) {
		Click cl = new Click(null, hash, new Date(System.currentTimeMillis()),
				null, null, null, ip, null);
		cl=clickRepository.save(cl);
		LOG.info(cl!=null?"["+hash+"] saved with id ["+cl.getId()+"]":"["+hash+"] was not saved");
	}

	private String extractIP(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l) {
		HttpHeaders h = new HttpHeaders();
		h.setLocation(URI.create(l.getTarget()));
		return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
	}

	@RequestMapping(value = "/link", method = RequestMethod.POST)
	public ResponseEntity<ShortURL> shortener(@RequestParam("url") String url,
											  @RequestParam(value = "sponsor", required = false) String sponsor,
											  HttpServletRequest request) {
		
		// Identify browser, browser version, and operating system.
	    UserAgent userAgent = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
	    Browser browser = userAgent.getBrowser();
	    String browserName = browser.getName();
	    Version browserVersion = userAgent.getBrowserVersion();
	    String version;
	    String os = userAgent.getOperatingSystem().getName();
	    if (browserVersion == null) {
	    	version = "test";
	    }
	    else version = browserVersion.toString();
	    String res = "+" + os + "+" + browserName + "+" + version;
	    res = res.replaceAll("\\s+","");
	    
	    
		ShortURL su = createAndSaveIfValid(url, sponsor, UUID
				.randomUUID().toString(), extractIP(request), res);

		if (su != null) {

			HttpHeaders h = new HttpHeaders();
			h.setLocation(su.getUri());
			return new ResponseEntity<>(su, h, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}

	private ShortURL createAndSaveIfValid(String url, String sponsor,
										  String owner, String ip, String res) {
		UrlValidator urlValidator = new UrlValidator(new String[] { "http",
				"https" });
		if (urlValidator.isValid(url)) {
			String id = Hashing.murmur3_32()
					.hashString(url, StandardCharsets.UTF_8).toString();

			ShortURL su = new ShortURL(id, url,
					linkTo(
							methodOn(UrlShortenerController.class).redirectTo(
									id + res, null)).toUri(), sponsor, new Date(
							System.currentTimeMillis()), owner,
					HttpStatus.TEMPORARY_REDIRECT.value(), true, ip, null, 0);

			return shortURLRepository.save(su);
		} else {
			return null;
		}
	}
}
