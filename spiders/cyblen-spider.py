import scrapy

number_of_pages_to_visit = 10
number_categories_to_visit = 1

# for parsing categories and urls of papers in these categories


class CatSpider(scrapy.Spider):
    name = 'aaa'
    start_urls = ['https://cyberleninka.ru/']

    def prepare_parse_paperlist(self, cat_name, url):
        f = open(cat_name + ".txt", "w")
        f.write(cat_name + '\n')

        return lambda x: \
            self.parse_paperlist(
                response=x,
                cat_name=cat_name,
                current_page=1,
                cat_url=url,
                f=f
            )

    def write_categories_to_file(self, catmap):
        catsf = open('cats.txt', 'w')
        for cat in list(catmap.items()):
            catsf.write(str(cat) + '\n')
        catsf.close()

    def parse(self, response):
        categories = response.css('.main>.half>.grnti, .main>.half-right>.grnti')
        names = []
        links = []
        for elem in categories.css("li>a"):
            names.append(elem.css("a::text").extract_first())
            links.append(elem.css("a::attr(href)").extract_first())

        def non_empty(x): return x is not None

        names = filter(non_empty, names)
        links = filter(non_empty, links)
        catmap = dict((x, y) for x, y in zip(names, links))

        self.write_categories_to_file(catmap)

        for cat_name, cat_link in list(catmap.items())[:number_categories_to_visit]:
            print(f'Parsing paperlist... {cat_name} - {cat_link}')

            url = response.urljoin(cat_link)
            parse_paperlist_func = self.prepare_parse_paperlist(cat_name, url)
            yield scrapy.Request(url, callback=parse_paperlist_func)

    def parse_paperlist(self, response, cat_name, current_page, cat_url, f):
        print(f'Parsing in category: {cat_name} - page: {current_page}')

        papers = response.css('.visible>.full>.list>li')
        paper_links = [paper.css('a::attr(href)').extract_first() for paper in papers]

        for link in paper_links:
            f.write(link + '\n')

        if current_page < number_of_pages_to_visit:
            yield scrapy.Request(
                cat_url + '/' + str(current_page + 1),
                callback=lambda x:
                    self.parse_paperlist(
                        response=x,
                        cat_name=cat_name,
                        current_page=current_page + 1,
                        cat_url=cat_url,
                        f=f
                    )
            )
        else:
            f.close()

    def parse_paper(self, response, cat_name, ff):
        title = response.css('div>h1>i::text').extract_first()
        authors = response.css('.author-list>li>span::text').extract()
        journal_name = response.css('.infoblock>.half>span>a::text').extract_first()
        year = response.css('.label year>time::text').extract_first()
        research_field = response.css('.half-right>ul>li>a::text').extract_first()
        references = response.css('.ocr>p::text').extract()

        # ff.write(link + '\n')
